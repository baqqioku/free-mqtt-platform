param(
  [string]$ClusterName = "default",
  [string]$ZkHost = "127.0.0.1",
  [int]$ZkPort = 2181,
  [string]$RedisHost = "127.0.0.1",
  [int]$RedisPort = 6379,
  [int]$RoutePort = 8084,
  [int]$HttpPort1 = 23240,
  [int]$HttpPort2 = 23241,
  [int]$TcpPort1 = 23242,
  [int]$TcpPort2 = 23243,
  [int]$ReliableQueueMaxSize = 0,
  [switch]$UseJava,
  [switch]$ShowWindows,
  [switch]$SkipStart,
  [switch]$SkipBuild,
  [switch]$Offline,
  [switch]$OfflineExpired,
  [switch]$AckCleanup,
  [switch]$RouteStoreFailover,
  [switch]$InflightNoAck,
  [switch]$QueueLimitTest,
  [switch]$IdempotentTest,
  [switch]$RetainTest,
  [switch]$WillTest,
  [switch]$NoCleanup
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $root
$logsDir = Join-Path $projectRoot "logs"
New-Item -ItemType Directory -Force $logsDir | Out-Null
$runId = (Get-Date -Format "yyyyMMdd_HHmmss") + "_" + (Get-Random)

$windowStyle = "Hidden"
if ($ShowWindows) {
  $windowStyle = "Normal"
}

function Get-FreeTcpPort {
  $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
  $listener.Start()
  $port = $listener.LocalEndpoint.Port
  $listener.Stop()
  return $port
}

function Test-PortAvailable([int]$Port) {
  try {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $Port)
    $listener.Start()
    $listener.Stop()
    return $true
  } catch {
    return $false
  }
}

foreach ($n in @("RoutePort","HttpPort1","HttpPort2","TcpPort1","TcpPort2")) {
  $v = Get-Variable -Name $n -ValueOnly
  if (-not (Test-PortAvailable $v)) {
    $newPort = Get-FreeTcpPort
    Set-Variable -Name $n -Value $newPort
  }
}

function Stop-ProjectJavaProcesses {
  try {
    $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
    foreach ($p in $procs) {
      try {
        if ($p.CommandLine -and $p.CommandLine.Contains($projectRoot)) {
          Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
        }
      } catch {
      }
    }
  } catch {
  }
}

$server1Log = Join-Path $logsDir "mqtt-server-1.$runId.log"
$server1ErrLog = Join-Path $logsDir "mqtt-server-1.$runId.err.log"
$server2Log = Join-Path $logsDir "mqtt-server-2.$runId.log"
$server2ErrLog = Join-Path $logsDir "mqtt-server-2.$runId.err.log"
$routeLog = Join-Path $logsDir "mqtt-route.$runId.log"
$routeErrLog = Join-Path $logsDir "mqtt-route.$runId.err.log"

function Assert-PortOpen([string]$TargetHost, [int]$Port, [string]$Name) {
  $ok = Test-NetConnection -ComputerName $TargetHost -Port $Port -InformationLevel Quiet
  if (-not $ok) {
    throw "$Name is not reachable: $TargetHost`:$Port"
  }
}

function Wait-Http([string]$Url, [int]$TimeoutSeconds = 30) {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      Invoke-WebRequest -UseBasicParsing -Method GET -Uri $Url -TimeoutSec 3 | Out-Null
      return
    } catch {
      if ($_.Exception -and $_.Exception.Response) {
        return
      }
      Start-Sleep -Milliseconds 300
    }
  }
  throw "HTTP not ready: $Url"
}

if (-not $SkipBuild) {
  Stop-ProjectJavaProcesses
  Write-Host "1) Maven package..."
  & mvn -q -DskipTests clean package
  if ($LASTEXITCODE -ne 0) { throw "mvn package failed (exitCode=$LASTEXITCODE)" }
  Write-Host "1.1) Compile mqtt-server smoke helper..."
  & mvn -q -pl mqtt-server -DskipTests test-compile
  if ($LASTEXITCODE -ne 0) { throw "mvn test-compile failed (exitCode=$LASTEXITCODE)" }
} else {
  Write-Host "1) SkipBuild enabled."
}

Write-Host "2) Check dependencies..."
Assert-PortOpen -TargetHost $ZkHost -Port $ZkPort -Name "ZooKeeper"
Assert-PortOpen -TargetHost $RedisHost -Port $RedisPort -Name "Redis"

$windowStyle = "Hidden"
$server1 = $null
$server2 = $null
$route = $null
$mqttClient = $null
$mqttClientA = $null
$mqttClientB = $null
$mqttClientC = $null
$server1Win = $null
$server2Win = $null
$routeWin = $null
$smokeOk = $false
$exitCode = 1

if (-not $SkipStart) {
  Write-Host "3) Start mqtt-server and mqtt-route..."

  Write-Host "Ports:"
  Write-Host "  mqtt-server-1: http=$HttpPort1 tcp=$TcpPort1"
  Write-Host "  mqtt-server-2: http=$HttpPort2 tcp=$TcpPort2"
  Write-Host "  mqtt-route:    http=$RoutePort"
  Write-Host "Logs:"
  Write-Host "  $server1Log"
  Write-Host "  $server1ErrLog"
  Write-Host "  $server2Log"
  Write-Host "  $server2ErrLog"
  Write-Host "  $routeLog"
  Write-Host "  $routeErrLog"

  foreach ($f in @($server1Log, $server1ErrLog, $server2Log, $server2ErrLog, $routeLog, $routeErrLog)) {
    try { Set-Content -Path $f -Value "" -Force } catch {}
  }

  $useJavaImpl = $UseJava -or (-not $PSBoundParameters.ContainsKey("UseJava"))
  if ($useJavaImpl) {
    Write-Host "Start mode: java -cp"

    $serverCpFile = Join-Path $projectRoot "mqtt-server\\target\\runtime-classpath.txt"
    if (-not (Test-Path $serverCpFile)) {
      & mvn -q -pl mqtt-server -DskipTests compile dependency:build-classpath -DincludeScope=runtime "-Dmdep.outputFile=$serverCpFile"
      if ($LASTEXITCODE -ne 0) { throw "build-classpath mqtt-server failed (exitCode=$LASTEXITCODE)" }
    }
    $serverDeps = Get-Content $serverCpFile -Raw
    $serverCp = (Join-Path $projectRoot "mqtt-server\\target\\classes") + ";" + $serverDeps.Trim()

    $routeCpFile = Join-Path $projectRoot "mqtt-route\\target\\runtime-classpath.txt"
    if (-not (Test-Path $routeCpFile)) {
      & mvn -q -pl mqtt-route -DskipTests compile dependency:build-classpath -DincludeScope=runtime "-Dmdep.outputFile=$routeCpFile"
      if ($LASTEXITCODE -ne 0) { throw "build-classpath mqtt-route failed (exitCode=$LASTEXITCODE)" }
    }
    $routeDeps = Get-Content $routeCpFile -Raw
    $routeCp = (Join-Path $projectRoot "mqtt-route\\target\\classes") + ";" + $routeDeps.Trim()

    $clientCpFile = Join-Path $projectRoot "mqtt-client\\target\\runtime-classpath.txt"
    if (-not (Test-Path $clientCpFile)) {
      & mvn -q -pl mqtt-client -DskipTests compile dependency:build-classpath -DincludeScope=runtime "-Dmdep.outputFile=$clientCpFile"
      if ($LASTEXITCODE -ne 0) { throw "build-classpath mqtt-client failed (exitCode=$LASTEXITCODE)" }
    }

    if ($ShowWindows) {
      $serverArgs1 = @(
        "-cp", $serverCp,
        "com.free.MqttBrokerApplication",
        "--server.port=$HttpPort1",
        "--tcpPort=$TcpPort1",
        "--zk.address=$ZkHost",
        "--clusterName=$ClusterName"
      )
      if ($ReliableQueueMaxSize -gt 0) { $serverArgs1 += "--mqtt.metric.config.reliableQueueMaxSize=$ReliableQueueMaxSize" }
      $server1 = Start-Process -FilePath "java" -ArgumentList $serverArgs1 -PassThru -WindowStyle Hidden -RedirectStandardOutput $server1Log -RedirectStandardError $server1ErrLog

      $serverArgs2 = @(
        "-cp", $serverCp,
        "com.free.MqttBrokerApplication",
        "--server.port=$HttpPort2",
        "--tcpPort=$TcpPort2",
        "--zk.address=$ZkHost",
        "--clusterName=$ClusterName"
      )
      if ($ReliableQueueMaxSize -gt 0) { $serverArgs2 += "--mqtt.metric.config.reliableQueueMaxSize=$ReliableQueueMaxSize" }
      $server2 = Start-Process -FilePath "java" -ArgumentList $serverArgs2 -PassThru -WindowStyle Hidden -RedirectStandardOutput $server2Log -RedirectStandardError $server2ErrLog

      $routeArgs = @(
        "-cp", $routeCp,
        "com.free.MqttRouteApplication",
        "--server.port=$RoutePort",
        "--zk.address=$ZkHost",
        "--clusterName=$ClusterName"
      )
      if ($ReliableQueueMaxSize -gt 0) { $routeArgs += "--mqtt.metric.config.reliableQueueMaxSize=$ReliableQueueMaxSize" }
      $route = Start-Process -FilePath "java" -ArgumentList $routeArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $routeLog -RedirectStandardError $routeErrLog

      $server1Win = Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-NoExit", "-Command", "Get-Content -Path '$server1Log' -Wait") -PassThru -WindowStyle Normal
      $server2Win = Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-NoExit", "-Command", "Get-Content -Path '$server2Log' -Wait") -PassThru -WindowStyle Normal
      $routeWin = Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-NoExit", "-Command", "Get-Content -Path '$routeLog' -Wait") -PassThru -WindowStyle Normal
    } else {
      $serverArgs1 = @(
        "-cp", $serverCp,
        "com.free.MqttBrokerApplication",
        "--server.port=$HttpPort1",
        "--tcpPort=$TcpPort1",
        "--zk.address=$ZkHost",
        "--clusterName=$ClusterName"
      )
      if ($ReliableQueueMaxSize -gt 0) { $serverArgs1 += "--mqtt.metric.config.reliableQueueMaxSize=$ReliableQueueMaxSize" }
      $server1 = Start-Process -FilePath "java" -ArgumentList $serverArgs1 -PassThru -WindowStyle Hidden -RedirectStandardOutput $server1Log -RedirectStandardError $server1ErrLog

      $serverArgs2 = @(
        "-cp", $serverCp,
        "com.free.MqttBrokerApplication",
        "--server.port=$HttpPort2",
        "--tcpPort=$TcpPort2",
        "--zk.address=$ZkHost",
        "--clusterName=$ClusterName"
      )
      if ($ReliableQueueMaxSize -gt 0) { $serverArgs2 += "--mqtt.metric.config.reliableQueueMaxSize=$ReliableQueueMaxSize" }
      $server2 = Start-Process -FilePath "java" -ArgumentList $serverArgs2 -PassThru -WindowStyle Hidden -RedirectStandardOutput $server2Log -RedirectStandardError $server2ErrLog

      $routeArgs = @(
        "-cp", $routeCp,
        "com.free.MqttRouteApplication",
        "--server.port=$RoutePort",
        "--zk.address=$ZkHost",
        "--clusterName=$ClusterName"
      )
      if ($ReliableQueueMaxSize -gt 0) { $routeArgs += "--mqtt.metric.config.reliableQueueMaxSize=$ReliableQueueMaxSize" }
      $route = Start-Process -FilePath "java" -ArgumentList $routeArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $routeLog -RedirectStandardError $routeErrLog
    }
  } else {
    Write-Host "Start mode: mvn spring-boot:run"

    $server1Cmd = "cd '$projectRoot'; mvn -q -pl mqtt-server spring-boot:run -Dspring-boot.run.arguments='--server.port=$HttpPort1 --tcpPort=$TcpPort1 --zk.address=$ZkHost --clusterName=$ClusterName'"
    if ($ShowWindows) {
      $server1Cmd = "$server1Cmd 2>&1 | Tee-Object -FilePath '$server1Log' -Append"
    } else {
      $server1Cmd = "$server1Cmd *>> '$server1Log'"
    }
    $server1 = Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-Command", $server1Cmd) -PassThru -WindowStyle $windowStyle

    $server2Cmd = "cd '$projectRoot'; mvn -q -pl mqtt-server spring-boot:run -Dspring-boot.run.arguments='--server.port=$HttpPort2 --tcpPort=$TcpPort2 --zk.address=$ZkHost --clusterName=$ClusterName'"
    if ($ShowWindows) {
      $server2Cmd = "$server2Cmd 2>&1 | Tee-Object -FilePath '$server2Log' -Append"
    } else {
      $server2Cmd = "$server2Cmd *>> '$server2Log'"
    }
    $server2 = Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-Command", $server2Cmd) -PassThru -WindowStyle $windowStyle

    $routeCmd = "cd '$projectRoot'; mvn -q -pl mqtt-route spring-boot:run -Dspring-boot.run.arguments='--server.port=$RoutePort --zk.address=$ZkHost --clusterName=$ClusterName'"
    if ($ShowWindows) {
      $routeCmd = "$routeCmd 2>&1 | Tee-Object -FilePath '$routeLog' -Append"
    } else {
      $routeCmd = "$routeCmd *>> '$routeLog'"
    }
    $route = Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-Command", $routeCmd) -PassThru -WindowStyle $windowStyle
  }

  Write-Host "PIDs:"
  Write-Host "  mqtt-server-1 pid=$($server1.Id)"
  Write-Host "  mqtt-server-2 pid=$($server2.Id)"
  Write-Host "  mqtt-route    pid=$($route.Id)"
  Write-Host "Tip: tail logs with: Get-Content -Path <logfile> -Wait"
} else {
  Write-Host "3) SkipStart enabled. Assume services already running."
  Write-Host "Ports:"
  Write-Host "  mqtt-server-1: http=$HttpPort1 tcp=$TcpPort1"
  Write-Host "  mqtt-server-2: http=$HttpPort2 tcp=$TcpPort2"
  Write-Host "  mqtt-route:    http=$RoutePort"
}

try {
  Write-Host "4) Wait route service..."
  Wait-Http -Url "http://127.0.0.1:$RoutePort/error" -TimeoutSeconds 60

  $userName = "smoke_" + (Get-Random)
  Write-Host "5) Call /register and /login..."

  $reg = $null
  try {
    $reg = Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$RoutePort/register" -ContentType "application/json" -Body (@{ userName = $userName } | ConvertTo-Json)
  } catch {
    $reg = Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$RoutePort/reqister" -ContentType "application/json" -Body (@{ userName = $userName } | ConvertTo-Json)
  }
  if ($reg.code -ne "200") { throw "register failed: $($reg | ConvertTo-Json -Compress)" }

  $token = $reg.dataBody.token
  if (-not $token) { throw "register missing token" }
  $userId = $reg.dataBody.userId
  if (-not $userId) { throw "register missing userId" }

  $login = $null
  $deadline = (Get-Date).AddSeconds(30)
  while ((Get-Date) -lt $deadline) {
    $login = Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$RoutePort/login" -ContentType "application/json" -Body (@{ userName = $userName; token = $token } | ConvertTo-Json)
    if ($login.code -eq "200" -and $login.dataBody -and $login.dataBody.tcpPort) { break }
    Start-Sleep -Milliseconds 300
  }
  if ($login.code -ne "200") { throw "login failed: $($login | ConvertTo-Json -Compress)" }

  $broker = $login.dataBody.brokerName
  $tcpPort = $login.dataBody.tcpPort
  $httpPort = $login.dataBody.httpPort
  $clientId = $login.dataBody.clientId

  if (-not $broker) { throw "login missing brokerName" }
  if (-not $tcpPort) { throw "login missing tcpPort" }
  if (-not $httpPort) { throw "login missing httpPort" }
  if (-not $clientId) { throw "login missing clientId" }

  Write-Host "OK: broker=$broker tcpPort=$tcpPort httpPort=$httpPort clientId=$clientId"

  if ($Offline) {
    if ($OfflineExpired) {
      Write-Host "6) Push offline message (expired) then start MQTT client..."
    } else {
      Write-Host "6) Push offline message then start MQTT client..."
    }
  } else {
    Write-Host "6) Start MQTT client and wait message..."
  }
  $marker = "smoke_marker_" + (Get-Random)
  $topic = "/broker/to/client/$userId"

  Assert-PortOpen -TargetHost "127.0.0.1" -Port $tcpPort -Name "Broker TCP"
  Assert-PortOpen -TargetHost "127.0.0.1" -Port $httpPort -Name "Broker HTTP"
  Assert-PortOpen -TargetHost "127.0.0.1" -Port $TcpPort1 -Name "Broker1 TCP"
  Assert-PortOpen -TargetHost "127.0.0.1" -Port $TcpPort2 -Name "Broker2 TCP"

  if ($OfflineExpired) { $Offline = $true }
  if ($QueueLimitTest -and $ReliableQueueMaxSize -le 0) { $ReliableQueueMaxSize = 5 }
  $prePush = $Offline -or $RouteStoreFailover -or $InflightNoAck -or $QueueLimitTest -or $IdempotentTest

  if ($RouteStoreFailover) {
    Write-Host "6) Stop selected broker to force route offline store + failover..."
    $otherTcpPort = $TcpPort1
    if ($tcpPort -eq $TcpPort1) { $otherTcpPort = $TcpPort2 }
    if ($tcpPort -eq $TcpPort1) {
      if ($server1) { try { Stop-Process -Id $server1.Id -Force -ErrorAction SilentlyContinue } catch {} }
    } else {
      if ($server2) { try { Stop-Process -Id $server2.Id -Force -ErrorAction SilentlyContinue } catch {} }
    }
    Start-Sleep -Milliseconds 800
    $tcpPort = $otherTcpPort
    $brokerUrl = "tcp://127.0.0.1:$tcpPort"
  }

  if ($prePush) {
    if ($QueueLimitTest) {
      Write-Host "6.1) QueueLimitTest: push burst messages..."
      $markerPrefix = "ql_" + $runId + "_"
      for ($i = 1; $i -le 10; $i++) {
        $pushBody = @{
          userId = [long]$userId
          messageId = $i
          ttl = 60
          data = @{ msg = "hello"; marker = ($markerPrefix + $i) }
          url = "http://example.invalid"
        } | ConvertTo-Json -Depth 10
        $pushRes = Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$RoutePort/pushMsg" -ContentType "application/json" -Body $pushBody
        if ($pushRes.code -ne "200") { throw "pushMsg failed: $($pushRes | ConvertTo-Json -Compress)" }
      }
      $marker = $markerPrefix
    } elseif ($IdempotentTest) {
      Write-Host "6.1) IdempotentTest: push same msgUUID multiple times..."
      $marker = "idem_" + $runId
      $fixedMsgUUID = "idem_uuid_" + $runId
      for ($i = 1; $i -le 3; $i++) {
        $pushBody = @{
          userId = [long]$userId
          messageId = 1
          ttl = 60
          msgUUID = $fixedMsgUUID
          data = @{ msg = "hello"; marker = $marker }
          url = "http://example.invalid"
        } | ConvertTo-Json -Depth 10
        $pushRes = Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$RoutePort/pushMsg" -ContentType "application/json" -Body $pushBody
        if ($pushRes.code -ne "200") { throw "pushMsg failed: $($pushRes | ConvertTo-Json -Compress)" }
      }
    } else {
      Write-Host "6.1) Call route /pushMsg (offline)..."
      $ttlSeconds = 60
      if ($OfflineExpired) { $ttlSeconds = 1 }
      $pushBody = @{
        userId = [long]$userId
        messageId = 1
        ttl = $ttlSeconds
        data = @{ msg = "hello"; marker = $marker }
        url = "http://example.invalid"
      } | ConvertTo-Json -Depth 10
      $pushRes = Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$RoutePort/pushMsg" -ContentType "application/json" -Body $pushBody
      if ($pushRes.code -ne "200") { throw "pushMsg failed: $($pushRes | ConvertTo-Json -Compress)" }
      if ($OfflineExpired) {
        Start-Sleep -Seconds 2
      }
    }
  }

  $runtimeCpFile = Join-Path $projectRoot "mqtt-client\\target\\runtime-classpath.txt"
  if (-not (Test-Path $runtimeCpFile)) {
    & mvn -q -pl mqtt-client -DskipTests compile dependency:build-classpath -DincludeScope=runtime "-Dmdep.outputFile=$runtimeCpFile"
    if ($LASTEXITCODE -ne 0) { throw "build-classpath mqtt-client failed (exitCode=$LASTEXITCODE)" }
  }
  $runtimeCp = Get-Content $runtimeCpFile -Raw
  $cp = (Join-Path $projectRoot "mqtt-client\\target\\classes") + ";" + $runtimeCp.Trim()

  $skipMainFlow = $false

  if ($InflightNoAck) {
    Write-Host "7) Inflight resend + throttle scenario (no PUBACK first time)..."
    $brokerUrl = "tcp://127.0.0.1:$tcpPort"

    $clientOutLogA = Join-Path $logsDir "mqtt-client.$runId.noack.out.log"
    $clientErrLogA = Join-Path $logsDir "mqtt-client.$runId.noack.err.log"
    $javaArgsA = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "noAck",
      "--brokerUrl", $brokerUrl,
      "--clientId", $clientId,
      "--userName", $userName,
      "--password", $token,
      "--topic", $topic,
      "--contains", $marker,
      "--timeoutSeconds", "10"
    )
    $mqttClientA = Start-Process -FilePath "java" -ArgumentList $javaArgsA -PassThru -WindowStyle Hidden -RedirectStandardOutput $clientOutLogA -RedirectStandardError $clientErrLogA
    $waitA = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $waitA -and -not $mqttClientA.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientA.HasExited) { try { Stop-Process -Id $mqttClientA.Id -Force } catch {}; throw "NoAck client timeout" }
    $payloadA = ""
    if (Test-Path $clientOutLogA) { $payloadA = $payloadA + (Get-Content $clientOutLogA -Raw) }
    if ($mqttClientA.ExitCode -ne 0 -or $payloadA -notmatch [Regex]::Escape($marker)) {
      throw "NoAck client failed, exitCode=$($mqttClientA.ExitCode) output=$payloadA"
    }

    $clientOutLogB = Join-Path $logsDir "mqtt-client.$runId.throttle.out.log"
    $clientErrLogB = Join-Path $logsDir "mqtt-client.$runId.throttle.err.log"
    $javaArgsB = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "subWait",
      "--brokerUrl", $brokerUrl,
      "--clientId", $clientId,
      "--userName", $userName,
      "--password", $token,
      "--topic", $topic,
      "--contains", $marker,
      "--timeoutSeconds", "1"
    )
    $mqttClientB = Start-Process -FilePath "java" -ArgumentList $javaArgsB -PassThru -WindowStyle Hidden -RedirectStandardOutput $clientOutLogB -RedirectStandardError $clientErrLogB
    $waitB = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $waitB -and -not $mqttClientB.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientB.HasExited) { try { Stop-Process -Id $mqttClientB.Id -Force } catch {} }
    $payloadB = ""
    if (Test-Path $clientOutLogB) { $payloadB = $payloadB + (Get-Content $clientOutLogB -Raw) }
    if ($payloadB -match [Regex]::Escape($marker)) { throw "Throttle failed: message delivered too soon" }

    Start-Sleep -Seconds 3

    $clientOutLogC = Join-Path $logsDir "mqtt-client.$runId.resend.out.log"
    $clientErrLogC = Join-Path $logsDir "mqtt-client.$runId.resend.err.log"
    $javaArgsC = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "subWait",
      "--brokerUrl", $brokerUrl,
      "--clientId", $clientId,
      "--userName", $userName,
      "--password", $token,
      "--topic", $topic,
      "--contains", $marker,
      "--timeoutSeconds", "10"
    )
    $mqttClientC = Start-Process -FilePath "java" -ArgumentList $javaArgsC -PassThru -WindowStyle Hidden -RedirectStandardOutput $clientOutLogC -RedirectStandardError $clientErrLogC
    $waitC = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $waitC -and -not $mqttClientC.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientC.HasExited) { try { Stop-Process -Id $mqttClientC.Id -Force } catch {}; throw "Resend client timeout" }
    $payloadC = ""
    if (Test-Path $clientOutLogC) { $payloadC = $payloadC + (Get-Content $clientOutLogC -Raw) }
    if ($mqttClientC.ExitCode -ne 0 -or $payloadC -notmatch [Regex]::Escape($marker)) {
      throw "Resend client failed, exitCode=$($mqttClientC.ExitCode) output=$payloadC"
    }

    Write-Host "OK: inflight throttle + resend verified"
    $smokeOk = $true
    $exitCode = 0
    $skipMainFlow = $true
  }

  if (-not $skipMainFlow -and $QueueLimitTest) {
    Write-Host "7) QueueLimitTest: verify only latest messages kept..."
    $brokerUrl = "tcp://127.0.0.1:$tcpPort"
    $expectedCount = [Math]::Min($ReliableQueueMaxSize, 10)
    $minIndex = 11 - $expectedCount
    $maxIndex = 10

    $clientOutLogA = Join-Path $logsDir "mqtt-client.$runId.queuelimit.out.log"
    $clientErrLogA = Join-Path $logsDir "mqtt-client.$runId.queuelimit.err.log"
    $javaArgsA = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "batchExpect",
      "--brokerUrl", $brokerUrl,
      "--clientId", $clientId,
      "--userName", $userName,
      "--password", $token,
      "--topic", $topic,
      "--markerPrefix", $marker,
      "--expectedCount", "$expectedCount",
      "--minIndex", "$minIndex",
      "--maxIndex", "$maxIndex",
      "--timeoutSeconds", "30"
    )
    $mqttClientA = Start-Process -FilePath "java" -ArgumentList $javaArgsA -PassThru -WindowStyle Hidden -RedirectStandardOutput $clientOutLogA -RedirectStandardError $clientErrLogA
    $waitA = (Get-Date).AddSeconds(40)
    while ((Get-Date) -lt $waitA -and -not $mqttClientA.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientA.HasExited) { try { Stop-Process -Id $mqttClientA.Id -Force } catch {}; throw "QueueLimitTest client timeout" }
    if ($mqttClientA.ExitCode -ne 0) {
      $out = ""
      if (Test-Path $clientOutLogA) { $out = $out + (Get-Content $clientOutLogA -Raw) }
      if (Test-Path $clientErrLogA) { $out = $out + (Get-Content $clientErrLogA -Raw) }
      throw "QueueLimitTest failed, exitCode=$($mqttClientA.ExitCode) output=$out"
    }
    Write-Host "OK: queue limit verified"
    $smokeOk = $true
    $exitCode = 0
    $skipMainFlow = $true
  }

  if (-not $skipMainFlow -and $IdempotentTest) {
    Write-Host "7) IdempotentTest: verify duplicate msgUUID stored once..."
    $brokerUrl = "tcp://127.0.0.1:$tcpPort"
    $clientOutLogA = Join-Path $logsDir "mqtt-client.$runId.idempotent.out.log"
    $clientErrLogA = Join-Path $logsDir "mqtt-client.$runId.idempotent.err.log"
    $javaArgsA = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "count",
      "--brokerUrl", $brokerUrl,
      "--clientId", $clientId,
      "--userName", $userName,
      "--password", $token,
      "--topic", $topic,
      "--contains", $marker,
      "--durationSeconds", "6",
      "--expectedCount", "1"
    )
    $mqttClientA = Start-Process -FilePath "java" -ArgumentList $javaArgsA -PassThru -WindowStyle Hidden -RedirectStandardOutput $clientOutLogA -RedirectStandardError $clientErrLogA
    $waitA = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $waitA -and -not $mqttClientA.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientA.HasExited) { try { Stop-Process -Id $mqttClientA.Id -Force } catch {}; throw "IdempotentTest client timeout" }
    if ($mqttClientA.ExitCode -ne 0) {
      $out = ""
      if (Test-Path $clientOutLogA) { $out = $out + (Get-Content $clientOutLogA -Raw) }
      if (Test-Path $clientErrLogA) { $out = $out + (Get-Content $clientErrLogA -Raw) }
      throw "IdempotentTest failed, exitCode=$($mqttClientA.ExitCode) output=$out"
    }
    Write-Host "OK: idempotent verified"
    $smokeOk = $true
    $exitCode = 0
    $skipMainFlow = $true
  }

  if (-not $skipMainFlow -and $RetainTest) {
    Write-Host "7) RetainTest: publish retained then subscribe should get retained message..."
    $brokerUrl = "tcp://127.0.0.1:$tcpPort"
    $retainTopic = "/retain/test/$userId"
    $retainMarker = "retain_" + $runId

    $pubOut = Join-Path $logsDir "mqtt-client.$runId.retainpub.out.log"
    $pubErr = Join-Path $logsDir "mqtt-client.$runId.retainpub.err.log"
    $pubArgs = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "pub",
      "--brokerUrl", $brokerUrl,
      "--clientId", ($clientId + "_retain_pub"),
      "--userName", $userName,
      "--password", $token,
      "--topic", $retainTopic,
      "--payload", $retainMarker,
      "--qos", "1",
      "--retain", "true"
    )
    $mqttClientA = Start-Process -FilePath "java" -ArgumentList $pubArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $pubOut -RedirectStandardError $pubErr
    $waitA = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $waitA -and -not $mqttClientA.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientA.HasExited) { try { Stop-Process -Id $mqttClientA.Id -Force } catch {}; throw "RetainTest publish client timeout" }
    if ($mqttClientA.ExitCode -ne 0) {
      $out = ""
      if (Test-Path $pubOut) { $out = $out + (Get-Content $pubOut -Raw) }
      if (Test-Path $pubErr) { $out = $out + (Get-Content $pubErr -Raw) }
      throw "RetainTest publish failed, exitCode=$($mqttClientA.ExitCode) output=$out"
    }

    $subOut = Join-Path $logsDir "mqtt-client.$runId.retainexpect.out.log"
    $subErr = Join-Path $logsDir "mqtt-client.$runId.retainexpect.err.log"
    $subArgs = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "retainExpect",
      "--brokerUrl", $brokerUrl,
      "--clientId", ($clientId + "_retain_sub"),
      "--userName", $userName,
      "--password", $token,
      "--topic", $retainTopic,
      "--contains", $retainMarker,
      "--expectRetained", "true",
      "--timeoutSeconds", "20"
    )
    $mqttClientB = Start-Process -FilePath "java" -ArgumentList $subArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $subOut -RedirectStandardError $subErr
    $waitB = (Get-Date).AddSeconds(25)
    while ((Get-Date) -lt $waitB -and -not $mqttClientB.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientB.HasExited) { try { Stop-Process -Id $mqttClientB.Id -Force } catch {}; throw "RetainTest expect client timeout" }
    if ($mqttClientB.ExitCode -ne 0) {
      $out = ""
      if (Test-Path $subOut) { $out = $out + (Get-Content $subOut -Raw) }
      if (Test-Path $subErr) { $out = $out + (Get-Content $subErr -Raw) }
      throw "RetainTest failed, exitCode=$($mqttClientB.ExitCode) output=$out"
    }

    Write-Host "OK: retain verified"
    $smokeOk = $true
    $exitCode = 0
    $skipMainFlow = $true
  }

  if (-not $skipMainFlow -and $WillTest) {
    Write-Host "7) WillTest: kill client should trigger will message..."
    $brokerUrl = "tcp://127.0.0.1:$tcpPort"
    $willTopic = "/will/test/$userId"
    $willMarker = "will_" + $runId

    $subOut = Join-Path $logsDir "mqtt-client.$runId.willsub.out.log"
    $subErr = Join-Path $logsDir "mqtt-client.$runId.willsub.err.log"
    $subArgs = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "subWait",
      "--brokerUrl", $brokerUrl,
      "--clientId", ($clientId + "_will_sub"),
      "--userName", $userName,
      "--password", $token,
      "--topic", $willTopic,
      "--contains", $willMarker,
      "--timeoutSeconds", "35"
    )
    $mqttClientA = Start-Process -FilePath "java" -ArgumentList $subArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $subOut -RedirectStandardError $subErr
    Start-Sleep -Milliseconds 800

    $pubOut = Join-Path $logsDir "mqtt-client.$runId.willpub.out.log"
    $pubErr = Join-Path $logsDir "mqtt-client.$runId.willpub.err.log"
    $pubArgs = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "willPublisher",
      "--brokerUrl", $brokerUrl,
      "--clientId", ($clientId + "_will_pub"),
      "--userName", $userName,
      "--password", $token,
      "--willTopic", $willTopic,
      "--willPayload", $willMarker,
      "--willQos", "1",
      "--willRetain", "false",
      "--sleepSeconds", "60"
    )
    $mqttClientB = Start-Process -FilePath "java" -ArgumentList $pubArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $pubOut -RedirectStandardError $pubErr
    Start-Sleep -Seconds 2
    try { Stop-Process -Id $mqttClientB.Id -Force -ErrorAction SilentlyContinue } catch {}

    $waitA = (Get-Date).AddSeconds(40)
    while ((Get-Date) -lt $waitA -and -not $mqttClientA.HasExited) { Start-Sleep -Milliseconds 200 }
    if (-not $mqttClientA.HasExited) { try { Stop-Process -Id $mqttClientA.Id -Force } catch {}; throw "WillTest subscriber timeout" }
    if ($mqttClientA.ExitCode -ne 0) {
      $out = ""
      if (Test-Path $subOut) { $out = $out + (Get-Content $subOut -Raw) }
      if (Test-Path $subErr) { $out = $out + (Get-Content $subErr -Raw) }
      throw "WillTest failed, exitCode=$($mqttClientA.ExitCode) output=$out"
    }

    Write-Host "OK: will verified"
    $smokeOk = $true
    $exitCode = 0
    $skipMainFlow = $true
  }

  if (-not $skipMainFlow) {
    $clientOutLog = Join-Path $logsDir "mqtt-client.$runId.out.log"
    $clientErrLog = Join-Path $logsDir "mqtt-client.$runId.err.log"
    $brokerUrl = "tcp://127.0.0.1:$tcpPort"
    $javaArgs = @(
      "-cp", $cp,
      "com.free.mqtt.client.app.FreeMqttClientApp",
      "--mode", "subWait",
      "--brokerUrl", $brokerUrl,
      "--clientId", $clientId,
      "--userName", $userName,
      "--password", $token,
      "--topic", $topic,
      "--contains", $marker,
      "--timeoutSeconds", "30"
    )
    $mqttClient = Start-Process -FilePath "java" -ArgumentList $javaArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $clientOutLog -RedirectStandardError $clientErrLog
  }

  if (-not $skipMainFlow) {
    $subDeadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $subDeadline) {
      $seen = $false
      foreach ($lf in @($server1Log, $server2Log)) {
        if (-not (Test-Path $lf)) { continue }
        try {
          $tail = Get-Content -Path $lf -Tail 200 -ErrorAction SilentlyContinue
          if (($tail -match [Regex]::Escape($clientId)) -and ($tail -match [Regex]::Escape($topic))) {
            $seen = $true
            break
          }
        } catch {}
      }
      if ($seen) { break }
      Start-Sleep -Milliseconds 200
    }

    if (-not $prePush) {
      Write-Host "7) Call route /pushMsg..."
      $pushBody = @{
        userId = [long]$userId
        messageId = 1
        ttl = 60
        data = @{ msg = "hello"; marker = $marker }
        url = "http://example.invalid"
      } | ConvertTo-Json -Depth 10
      $pushRes = Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$RoutePort/pushMsg" -ContentType "application/json" -Body $pushBody
      if ($pushRes.code -ne "200") { throw "pushMsg failed: $($pushRes | ConvertTo-Json -Compress)" }
    }

    Write-Host "8) Wait MQTT client exit..."
    $waitDeadline = (Get-Date).AddSeconds(35)
    while ((Get-Date) -lt $waitDeadline -and -not $mqttClient.HasExited) {
      Start-Sleep -Milliseconds 200
    }
    if (-not $mqttClient.HasExited) {
      try { Stop-Process -Id $mqttClient.Id -Force } catch {}
      throw "MQTT client timeout, message not received"
    }
    $payload = ""
    if (Test-Path $clientOutLog) { $payload = $payload + (Get-Content $clientOutLog -Raw) }
    if (Test-Path $clientErrLog) { $payload = $payload + (Get-Content $clientErrLog -Raw) }
    if ($OfflineExpired) {
      if ($payload -match [Regex]::Escape($marker)) { throw "Expired message should not be delivered" }
      if ($mqttClient.ExitCode -eq 0) { throw "Expired message should not be delivered" }
      Write-Host "OK: expired message not delivered"
      $smokeOk = $true
      $exitCode = 0
    } else {
      if ($mqttClient.ExitCode -ne 0) {
        throw "MQTT client verification failed, exitCode=$($mqttClient.ExitCode) output=$payload"
      }
      if ($payload -match "TIMEOUT") { throw "MQTT client timeout, message not received" }
      if ($payload -match "MQTT error") { throw "MQTT client error" }
      if ($payload -notmatch [Regex]::Escape($marker)) { throw "Received message but marker missing" }
      Write-Host "OK: pushMsg delivered"
      $smokeOk = $true
      $exitCode = 0
    }

    if ($AckCleanup -and -not $Offline -and -not $OfflineExpired) {
      Write-Host "8.1) Verify ack cleanup (should not deliver again)..."
      $clientOutLog2 = Join-Path $logsDir "mqtt-client.$runId.ackcheck.out.log"
      $clientErrLog2 = Join-Path $logsDir "mqtt-client.$runId.ackcheck.err.log"
      $javaArgs2 = @(
        "-cp", $cp,
        "com.free.mqtt.client.app.FreeMqttClientApp",
        "--mode", "subWait",
        "--brokerUrl", $brokerUrl,
        "--clientId", $clientId,
        "--userName", $userName,
        "--password", $token,
        "--topic", $topic,
        "--contains", $marker,
        "--timeoutSeconds", "5"
      )
      $mqttClient2 = Start-Process -FilePath "java" -ArgumentList $javaArgs2 -PassThru -WindowStyle Hidden -RedirectStandardOutput $clientOutLog2 -RedirectStandardError $clientErrLog2
      $waitDeadline2 = (Get-Date).AddSeconds(10)
      while ((Get-Date) -lt $waitDeadline2 -and -not $mqttClient2.HasExited) {
        Start-Sleep -Milliseconds 200
      }
      if (-not $mqttClient2.HasExited) {
        try { Stop-Process -Id $mqttClient2.Id -Force } catch {}
        throw "Ack cleanup check client timeout"
      }
      $payload2 = ""
      if (Test-Path $clientOutLog2) { $payload2 = $payload2 + (Get-Content $clientOutLog2 -Raw) }
      if (Test-Path $clientErrLog2) { $payload2 = $payload2 + (Get-Content $clientErrLog2 -Raw) }
      if ($payload2 -match [Regex]::Escape($marker)) { throw "Ack cleanup failed: message delivered twice" }
      if ($mqttClient2.ExitCode -eq 0) { throw "Ack cleanup failed: message delivered twice" }
      Write-Host "OK: ack cleanup verified"
    }
  }
} catch {
  $exitCode = 1
  Write-Host "FAILED: $($_.Exception.Message)"
} finally {
  Write-Host "9) Stop processes..."
  if (-not $NoCleanup) {
    foreach ($p in @($mqttClientC, $mqttClientB, $mqttClientA, $mqttClient, $routeWin, $server2Win, $server1Win, $route, $server2, $server1)) {
      try {
        if ($p) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
      } catch {}
    }
  } else {
    Write-Host "NoCleanup enabled. Processes left running."
  }
}

exit $exitCode
