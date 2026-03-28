param(
    [string]$RepoLocal = "D:\myEclipseWorkspaces\free-mqtt-platform\.m2",
    [string]$ClusterName = "dev",
    [string]$ZkAddress = "127.0.0.1:2181",
    [string]$Broker1ServerPort = "23240",
    [string]$Broker1TcpPort = "23242",
    [string]$Broker2ServerPort = "23250",
    [string]$Broker2TcpPort = "23252",
    [string]$RoutePort = "8084"
)

$ErrorActionPreference = "Stop"

function Wait-Port {
    param([int]$Port, [int]$TimeoutSec = 60)
    $start = Get-Date
    while ((Get-Date) -lt $start.AddSeconds($TimeoutSec)) {
        try {
            $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue
            if ($tcp.TcpTestSucceeded) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Test-BindablePort {
    param([int]$Port)
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        $listener.Stop()
        return $true
    } catch {
        return $false
    }
}

function Pick-FreePort {
    for ($i = 0; $i -lt 50; $i++) {
        $candidate = Get-Random -Minimum 20000 -Maximum 40000
        if (Test-BindablePort -Port $candidate) { return $candidate }
    }
    throw "Unable to find a free port."
}

$zkHost = $ZkAddress.Split(':')[0]
$zkPort = 2181
if ($ZkAddress.Contains(':')) { $zkPort = [int]$ZkAddress.Split(':')[1] }
$redisUp = Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -InformationLevel Quiet
$zkUp = Test-NetConnection -ComputerName $zkHost -Port $zkPort -InformationLevel Quiet
if (-not $zkUp) { Write-Host "Warning: Zookeeper not reachable at $ZkAddress" }
if (-not $redisUp) { Write-Host "Warning: Redis not reachable at 127.0.0.1:6379" }

$Broker1ServerPort = [string](Pick-FreePort)
$Broker2ServerPort = [string](Pick-FreePort)
$Broker1TcpPort = [string](Pick-FreePort)
$Broker2TcpPort = [string](Pick-FreePort)
$RoutePort = [string](Pick-FreePort)

Write-Host "Using ports: broker-1 http=$Broker1ServerPort tcp=$Broker1TcpPort; broker-2 http=$Broker2ServerPort tcp=$Broker2TcpPort; route=$RoutePort"

$logsDir = "logs"
if (-not (Test-Path -Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir | Out-Null
}

"broker1.http=$Broker1ServerPort`nbroker1.tcp=$Broker1TcpPort`nbroker2.http=$Broker2ServerPort`nbroker2.tcp=$Broker2TcpPort`nroute.http=$RoutePort`nzk=$ZkAddress" | Set-Content -Path (Join-Path $logsDir "ports.txt")

Write-Host "Packaging (skip tests) with local repo $RepoLocal ..."
$packageCmd = "mvn -DskipTests -Dmaven.repo.local=`"$RepoLocal`" package"
cmd /c $packageCmd

Write-Host "Installing mqtt-common into local repo (skip tests) ..."
$installCommonCmd = "mvn -DskipTests -Dmaven.repo.local=`"$RepoLocal`" -pl mqtt-common -am install"
cmd /c $installCommonCmd

Write-Host "Copying runtime dependencies (shorter classpath)..."
cmd /c "mvn -Dmaven.repo.local=`"$RepoLocal`" -pl mqtt-server -am dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\lib"
cmd /c "mvn -Dmaven.repo.local=`"$RepoLocal`" -pl mqtt-route -am dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\lib"

$serverLib = "mqtt-server\target\lib"
$routeLib = "mqtt-route\target\lib"
if (-not (Test-Path -Path $serverLib) -or -not (Test-Path -Path $routeLib)) {
    throw "Dependency copy failed. Ensure target\\lib directories are created."
}

$serverCp = "mqtt-server\target\classes;mqtt-server\target\lib\*"
$routeCp = "mqtt-route\target\classes;mqtt-route\target\lib\*"

# Workaround for PATH/Path collision in Start-Process
if ($env:PATH -and $env:Path) { Remove-Item Env:PATH }
$javaPath = (Get-Command java).Source

Write-Host "Starting broker-1 (background)..."
$broker1 = Start-Process -FilePath $javaPath -ArgumentList @("-cp", $serverCp, "com.free.MqttBrokerApplication", "--server.port=$Broker1ServerPort", "--tcpPort=$Broker1TcpPort", "--clusterName=$ClusterName", "--zk.address=$ZkAddress") -WorkingDirectory $PWD -RedirectStandardOutput "logs\broker-1.out.log" -RedirectStandardError "logs\broker-1.err.log" -PassThru
Write-Host "broker-1 pid=$($broker1.Id)"

Write-Host "Starting broker-2 (background)..."
$broker2 = Start-Process -FilePath $javaPath -ArgumentList @("-cp", $serverCp, "com.free.MqttBrokerApplication", "--server.port=$Broker2ServerPort", "--tcpPort=$Broker2TcpPort", "--clusterName=$ClusterName", "--zk.address=$ZkAddress") -WorkingDirectory $PWD -RedirectStandardOutput "logs\broker-2.out.log" -RedirectStandardError "logs\broker-2.err.log" -PassThru
Write-Host "broker-2 pid=$($broker2.Id)"

Write-Host "Starting route (background)..."
$route = Start-Process -FilePath $javaPath -ArgumentList @("-cp", $routeCp, "com.free.MqttRouteApplication", "--server.port=$RoutePort", "--clusterName=$ClusterName", "--zk.address=$ZkAddress") -WorkingDirectory $PWD -RedirectStandardOutput "logs\route.out.log" -RedirectStandardError "logs\route.err.log" -PassThru
Write-Host "route pid=$($route.Id)"

Write-Host "Waiting for services to boot..."
$okBroker1 = Wait-Port -Port ([int]$Broker1ServerPort) -TimeoutSec 60
$okBroker2 = Wait-Port -Port ([int]$Broker2ServerPort) -TimeoutSec 60
$okRoute = Wait-Port -Port ([int]$RoutePort) -TimeoutSec 60
if (-not $okBroker1 -or -not $okBroker2 -or -not $okRoute) {
    throw "One or more services did not start. Check logs in .\\logs\\*.err.log for errors."
}

Write-Host "Registering users and logging in..."
$user1 = "u1_" + ([Guid]::NewGuid().ToString("N").Substring(0, 8))
$user2 = "u2_" + ([Guid]::NewGuid().ToString("N").Substring(0, 8))

$u1 = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/reqister" -ContentType "application/json" -Body (@{userName=$user1} | ConvertTo-Json)
$u2 = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/reqister" -ContentType "application/json" -Body (@{userName=$user2} | ConvertTo-Json)

Write-Host "Register responses (full JSON):"
($u1 | ConvertTo-Json -Depth 6) | Write-Host
($u2 | ConvertTo-Json -Depth 6) | Write-Host

$login1 = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/login" -ContentType "application/json" -Body (@{userName=$user1; token=$u1.dataBody.token} | ConvertTo-Json)
$login2 = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/login" -ContentType "application/json" -Body (@{userName=$user2; token=$u2.dataBody.token} | ConvertTo-Json)

Write-Host "Login responses (full JSON):"
($login1 | ConvertTo-Json -Depth 6) | Write-Host
($login2 | ConvertTo-Json -Depth 6) | Write-Host

Write-Host "Failover test: stop broker-1 (pid $($broker1.Id)), wait ~70s, then re-login and push a message."
try {
    Stop-Process -Id $broker1.Id -Force -ErrorAction Stop
    Write-Host "broker-1 stopped."
} catch {
    Write-Host "Warning: failed to stop broker-1 (pid $($broker1.Id)). $($_.Exception.Message)"
}

Start-Sleep -Seconds 70

Write-Host "Re-login after broker-1 stop..."
$login1After = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/login" -ContentType "application/json" -Body (@{userName=$user1; token=$u1.dataBody.token} | ConvertTo-Json)
Write-Host "Login response after broker-1 stop (full JSON):"
($login1After | ConvertTo-Json -Depth 6) | Write-Host

$pushBody = @{
    userId = $u1.dataBody.userId
    messageId = (Get-Random -Minimum 1 -Maximum 100000)
    ttl = 60000
    data = @{ text = "failover-test" }
}

Write-Host "Calling /pushMsg after broker-1 stop..."
$pushResp = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/pushMsg" -ContentType "application/json" -Body ($pushBody | ConvertTo-Json -Depth 6)
Write-Host "pushMsg response (full JSON):"
($pushResp | ConvertTo-Json -Depth 6) | Write-Host

