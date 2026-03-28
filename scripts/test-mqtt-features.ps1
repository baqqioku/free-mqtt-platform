param(
    [string]$RepoLocal = "D:\myEclipseWorkspaces\free-mqtt-platform\.m2",
    [string]$ZkAddress = "127.0.0.1:2181",
    [string]$RedisPassword = "123"
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

function Test-Redis {
    param(
        [string]$RedisHost = "127.0.0.1",
        [int]$Port = 6379,
        [string]$Password = "123"
    )
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect($RedisHost, $Port)
        $stream = $client.GetStream()
        $enc = [Text.Encoding]::ASCII

        function Send-Resp {
            param([System.Net.Sockets.NetworkStream]$NetStream, [string[]]$Parts)
            $sb = New-Object System.Text.StringBuilder
            [void]$sb.Append("*" + $Parts.Length + "`r`n")
            foreach ($part in $Parts) {
                $bytes = $enc.GetBytes($part)
                [void]$sb.Append("`$" + $bytes.Length + "`r`n")
                [void]$sb.Append($part + "`r`n")
            }
            $payload = $enc.GetBytes($sb.ToString())
            $NetStream.Write($payload, 0, $payload.Length)
            Start-Sleep -Milliseconds 200
            $buf = New-Object byte[] 256
            $len = $NetStream.Read($buf, 0, $buf.Length)
            if ($len -le 0) {
                return ""
            }
            return $enc.GetString($buf, 0, $len)
        }

        if ($Password -and $Password.Trim().Length -gt 0) {
            $resp = Send-Resp -NetStream $stream -Parts @("AUTH", $Password)
            if (-not ($resp.StartsWith("+OK") -or $resp.StartsWith("+PONG"))) {
                throw "AUTH failed: $resp"
            }
        }

        $resp2 = Send-Resp -NetStream $stream -Parts @("PING")
        $stream.Close()
        $client.Close()
        return $resp2.StartsWith("+PONG")
    } catch {
        return $false
    }
}

$zkHost = $ZkAddress.Split(':')[0]
$zkPort = 2181
if ($ZkAddress.Contains(':')) { $zkPort = [int]$ZkAddress.Split(':')[1] }
$redisUp = Test-Redis -RedisHost "127.0.0.1" -Port 6379 -Password $RedisPassword
$zkUp = Test-NetConnection -ComputerName $zkHost -Port $zkPort -InformationLevel Quiet
if (-not $zkUp) { Write-Host "Warning: Zookeeper not reachable at $ZkAddress" }
if (-not $redisUp) { throw "Redis not responding to AUTH/PING at 127.0.0.1:6379. Check service and password." }

$BrokerServerPort = [string](Pick-FreePort)
$BrokerTcpPort = [string](Pick-FreePort)
$BrokerSslPort = [string](Pick-FreePort)
$RoutePort = [string](Pick-FreePort)

Write-Host "Using ports: broker http=$BrokerServerPort tcp=$BrokerTcpPort ssl=$BrokerSslPort; route=$RoutePort"

$logsDir = "logs"
if (-not (Test-Path -Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir | Out-Null
}

Write-Host "Packaging (skip tests) with local repo $RepoLocal ..."
cmd /c "mvn -DskipTests -Dmaven.repo.local=`"$RepoLocal`" package"
if ($LASTEXITCODE -ne 0) { throw "Maven package failed" }

Write-Host "Installing mqtt-common into local repo (skip tests) ..."
cmd /c "mvn -DskipTests -Dmaven.repo.local=`"$RepoLocal`" -pl mqtt-common -am install"
if ($LASTEXITCODE -ne 0) { throw "Maven install mqtt-common failed" }

Write-Host "Copying runtime dependencies..."
cmd /c "mvn -Dmaven.repo.local=`"$RepoLocal`" -pl mqtt-server -am dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\lib"
if ($LASTEXITCODE -ne 0) { throw "Copy dependencies (mqtt-server) failed" }
cmd /c "mvn -Dmaven.repo.local=`"$RepoLocal`" -pl mqtt-route -am dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\lib"
if ($LASTEXITCODE -ne 0) { throw "Copy dependencies (mqtt-route) failed" }
cmd /c "mvn -Dmaven.repo.local=`"$RepoLocal`" -pl mqtt-client -am dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\lib"
if ($LASTEXITCODE -ne 0) { throw "Copy dependencies (mqtt-client) failed" }

$serverCp = "mqtt-server\target\classes;mqtt-server\target\lib\*"
$routeCp = "mqtt-route\target\classes;mqtt-route\target\lib\*"
$clientCp = "mqtt-client\target\classes;mqtt-client\target\lib\*"

$aclFile = Join-Path $logsDir "acl-test.conf"
@'
allow all pubsub /broker/to/client/#
allow all pubsub /allow/#
deny all pub /deny/#
'@ | Set-Content -Encoding utf8 $aclFile

# Workaround for PATH/Path collision in Start-Process
if ($env:PATH -and $env:Path) { Remove-Item Env:PATH }
$javaPath = (Get-Command java).Source

Write-Host "Starting broker (background)..."
$broker = Start-Process -FilePath $javaPath -ArgumentList @(
    "-cp", $serverCp, "com.free.MqttBrokerApplication",
    "--server.port=$BrokerServerPort",
    "--tcpPort=$BrokerTcpPort",
    "--tcpSslTcpPort=$BrokerSslPort",
    "--zk.address=$ZkAddress",
    "--spring.redis.password=$RedisPassword",
    "--cluster.enabled=false",
    "--cluster.local.brokerName=broker-local",
    "--mqtt.ssl.enabled=true",
    "--mqtt.acl.enabled=true",
    "--mqtt.acl.file=$aclFile",
    "--mqtt.will.delaySeconds=5"
) -WorkingDirectory $PWD -RedirectStandardOutput "logs\broker.out.log" -RedirectStandardError "logs\broker.err.log" -PassThru
Write-Host "broker pid=$($broker.Id)"

Write-Host "Starting route (background)..."
$route = Start-Process -FilePath $javaPath -ArgumentList @(
    "-cp", $routeCp, "com.free.MqttRouteApplication",
    "--server.port=$RoutePort",
    "--zk.address=$ZkAddress",
    "--spring.redis.password=$RedisPassword",
    "--cluster.enabled=false",
    "--route.static.enabled=true",
    "--route.static.brokerName=broker-local",
    "--route.static.host=127.0.0.1",
    "--route.static.tcpPort=$BrokerTcpPort",
    "--route.static.httpPort=$BrokerServerPort"
) -WorkingDirectory $PWD -RedirectStandardOutput "logs\route.out.log" -RedirectStandardError "logs\route.err.log" -PassThru
Write-Host "route pid=$($route.Id)"

Write-Host "Waiting for services to boot..."
$okBroker = Wait-Port -Port ([int]$BrokerServerPort) -TimeoutSec 120
$okRoute = Wait-Port -Port ([int]$RoutePort) -TimeoutSec 120
if (-not $okBroker -or -not $okRoute) {
    throw "Service did not start. Check logs in .\\logs\\*.err.log for errors."
}

Write-Host "Registering user and logging in..."
$user = "u_" + ([Guid]::NewGuid().ToString("N").Substring(0, 8))
$reg = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/reqister" -ContentType "application/json" -Body (@{userName=$user} | ConvertTo-Json)
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/login" -ContentType "application/json" -Body (@{userName=$user; token=$reg.dataBody.token} | ConvertTo-Json)

$userId = $reg.dataBody.userId
$token = $reg.dataBody.token
$clientId = $login.dataBody.clientId

Write-Host "Offline persistence + replay..."
$offlinePayload = "offline-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
$pushBody = @{
    userId = $userId
    messageId = (Get-Random -Minimum 1 -Maximum 100000)
    ttl = 60
    data = @{ text = $offlinePayload }
}
Invoke-RestMethod -Method Post -Uri "http://localhost:$RoutePort/pushMsg" -ContentType "application/json" -Body ($pushBody | ConvertTo-Json -Depth 6) | Out-Null
cmd /c "java -cp $clientCp com.free.mqtt.client.app.FreeMqttClientApp --mode subWait --routeUrl http://localhost:$RoutePort --userName $user --token $token --topic /broker/to/client/$userId --contains $offlinePayload --timeoutSeconds 20"
if ($LASTEXITCODE -ne 0) { throw "Offline replay failed" }

Write-Host "Retain verify..."
$retainPayload = "retain-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
cmd /c "java -cp $clientCp com.free.mqtt.client.app.FreeMqttClientApp --mode pub --routeUrl http://localhost:$RoutePort --userName $user --token $token --topic /allow/retain --payload $retainPayload --qos 1 --retain true"
cmd /c "java -cp $clientCp com.free.mqtt.client.app.FreeMqttClientApp --mode retainExpect --routeUrl http://localhost:$RoutePort --userName $user --token $token --topic /allow/retain --contains $retainPayload --expectRetained true --timeoutSeconds 20"
if ($LASTEXITCODE -ne 0) { throw "Retain check failed" }

Write-Host "ACL allow/deny verify..."
$allowPayload = "allow-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
$allowSubOut = "logs\acl-allow-sub.out.log"
$allowSubErr = "logs\acl-allow-sub.err.log"
try { Remove-Item $allowSubOut, $allowSubErr -Force -ErrorAction SilentlyContinue } catch {}
$allowSub = Start-Process -FilePath $javaPath -ArgumentList @(
    "-cp", $clientCp, "com.free.mqtt.client.app.FreeMqttClientApp",
    "--mode", "subWait",
    "--routeUrl", "http://localhost:$RoutePort",
    "--userName", $user,
    "--token", $token,
    "--topic", "/allow/test",
    "--contains", $allowPayload,
    "--timeoutSeconds", "20"
) -WorkingDirectory $PWD -RedirectStandardOutput $allowSubOut -RedirectStandardError $allowSubErr -PassThru
Start-Sleep -Seconds 2
cmd /c "java -cp $clientCp com.free.mqtt.client.app.FreeMqttClientApp --mode pub --routeUrl http://localhost:$RoutePort --userName $user --token $token --topic /allow/test --payload $allowPayload --qos 1"
if ($LASTEXITCODE -ne 0) { throw "ACL allow publish failed" }
$allowExited = $allowSub.WaitForExit(25000)
if (-not $allowExited) {
    try { Stop-Process -Id $allowSub.Id -Force -ErrorAction Stop } catch {}
    throw "ACL allow subscriber timeout"
}
$allowOutText = ""
if (Test-Path $allowSubOut) { $allowOutText = (Get-Content $allowSubOut -Raw) }
if ($allowOutText -notmatch [Regex]::Escape($allowPayload)) {
    $allowErrText = ""
    if (Test-Path $allowSubErr) { $allowErrText = (Get-Content $allowSubErr -Raw) }
    throw "ACL allow check failed: $allowErrText"
}

$denyPayload = "deny-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
$denySubOut = "logs\acl-deny-sub.out.log"
$denySubErr = "logs\acl-deny-sub.err.log"
try { Remove-Item $denySubOut, $denySubErr -Force -ErrorAction SilentlyContinue } catch {}
$denySub = Start-Process -FilePath $javaPath -ArgumentList @(
    "-cp", $clientCp, "com.free.mqtt.client.app.FreeMqttClientApp",
    "--mode", "subWait",
    "--routeUrl", "http://localhost:$RoutePort",
    "--userName", $user,
    "--token", $token,
    "--topic", "/deny/test",
    "--contains", $denyPayload,
    "--timeoutSeconds", "8"
) -WorkingDirectory $PWD -RedirectStandardOutput $denySubOut -RedirectStandardError $denySubErr -PassThru
Start-Sleep -Seconds 2
$denyPubOut = "logs\acl-deny-pub.out.log"
$denyPubErr = "logs\acl-deny-pub.err.log"
try { Remove-Item $denyPubOut, $denyPubErr -Force -ErrorAction SilentlyContinue } catch {}
$denyPub = Start-Process -FilePath $javaPath -ArgumentList @(
    "-cp", $clientCp, "com.free.mqtt.client.app.FreeMqttClientApp",
    "--mode", "pub",
    "--routeUrl", "http://localhost:$RoutePort",
    "--userName", $user,
    "--token", $token,
    "--topic", "/deny/test",
    "--payload", $denyPayload,
    "--qos", "1"
) -WorkingDirectory $PWD -RedirectStandardOutput $denyPubOut -RedirectStandardError $denyPubErr -PassThru

$denySubExited = $denySub.WaitForExit(15000)
if (-not $denySubExited) {
    try { Stop-Process -Id $denySub.Id -Force -ErrorAction Stop } catch {}
    throw "ACL deny subscriber timeout"
}
$denyOutText = ""
if (Test-Path $denySubOut) { $denyOutText = (Get-Content $denySubOut -Raw) }
if ($denyOutText -match [Regex]::Escape($denyPayload)) {
    throw "ACL deny check failed: subscriber received a denied publish"
}
try {
    if (-not $denyPub.WaitForExit(5000)) {
        Stop-Process -Id $denyPub.Id -Force -ErrorAction Stop
    }
} catch {}

Write-Host "Will delay verify..."
$willPayload = "will-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
Start-Process -FilePath $javaPath -ArgumentList @(
    "-cp", $clientCp, "com.free.mqtt.client.app.FreeMqttClientApp",
    "--mode", "willPublisher",
    "--routeUrl", "http://localhost:$RoutePort",
    "--userName", $user,
    "--token", $token,
    "--willTopic", "/allow/will",
    "--willPayload", $willPayload,
    "--willQos", "1",
    "--forceExit", "true",
    "--sleepSeconds", "2"
) -WorkingDirectory $PWD | Out-Null
cmd /c "java -cp $clientCp com.free.mqtt.client.app.FreeMqttClientApp --mode subWait --routeUrl http://localhost:$RoutePort --userName $user --token $token --topic /allow/will --contains $willPayload --timeoutSeconds 20"
if ($LASTEXITCODE -ne 0) { throw "Will delay check failed" }

Write-Host "SSL verify..."
$sslPayload = "ssl-" + ([Guid]::NewGuid().ToString("N").Substring(0, 6))
$sslSubClientId = ([Guid]::NewGuid().ToString("N")) + "_$userId"
$sslPubClientId = ([Guid]::NewGuid().ToString("N")) + "_$userId"
$sslSubOut = "logs\ssl-sub.out.log"
$sslSubErr = "logs\ssl-sub.err.log"
try { Remove-Item $sslSubOut, $sslSubErr -Force -ErrorAction SilentlyContinue } catch {}
$sslSub = Start-Process -FilePath $javaPath -ArgumentList @(
    "-cp", $clientCp, "com.free.mqtt.client.app.FreeMqttClientApp",
    "--mode", "subWait",
    "--brokerUrl", "ssl://127.0.0.1:$BrokerSslPort",
    "--clientId", $sslSubClientId,
    "--userName", $user,
    "--password", $token,
    "--topic", "/allow/ssl",
    "--contains", $sslPayload,
    "--ssl", "true",
    "--sslInsecure", "true",
    "--timeoutSeconds", "20"
) -WorkingDirectory $PWD -RedirectStandardOutput $sslSubOut -RedirectStandardError $sslSubErr -PassThru
Start-Sleep -Seconds 2
cmd /c "java -cp $clientCp com.free.mqtt.client.app.FreeMqttClientApp --mode pub --brokerUrl ssl://127.0.0.1:$BrokerSslPort --clientId $sslPubClientId --userName $user --password $token --topic /allow/ssl --payload $sslPayload --qos 1 --ssl true --sslInsecure true"
if ($LASTEXITCODE -ne 0) { throw "SSL publish failed" }
$sslExited = $sslSub.WaitForExit(25000)
if (-not $sslExited) {
    try { Stop-Process -Id $sslSub.Id -Force -ErrorAction Stop } catch {}
    throw "SSL subscriber timeout"
}
$sslOutText = ""
if (Test-Path $sslSubOut) { $sslOutText = (Get-Content $sslSubOut -Raw) }
if ($sslOutText -notmatch [Regex]::Escape($sslPayload)) {
    $sslErrText = ""
    if (Test-Path $sslSubErr) { $sslErrText = (Get-Content $sslSubErr -Raw) }
    throw "SSL check failed: $sslErrText"
}

Write-Host "All feature checks passed."

Write-Host "Stopping services..."
try { Stop-Process -Id $broker.Id -Force -ErrorAction Stop } catch {}
try { Stop-Process -Id $route.Id -Force -ErrorAction Stop } catch {}

