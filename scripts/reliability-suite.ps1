param(
  [switch]$SkipBuild,
  [string]$ClusterName = "default",
  [string]$ZkHost = "127.0.0.1",
  [int]$ZkPort = 2181,
  [string]$RedisHost = "127.0.0.1",
  [int]$RedisPort = 6379,
  [int]$RoutePort = 8084,
  [int]$HttpPort1 = 23240,
  [int]$HttpPort2 = 23241,
  [int]$TcpPort1 = 23242,
  [int]$TcpPort2 = 23243
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$clusterSmoke = Join-Path $root "cluster-smoke.ps1"
if (-not (Test-Path $clusterSmoke)) { throw "Missing script: $clusterSmoke" }

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
  $projectRoot = Split-Path -Parent $root
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

if (-not $SkipBuild) {
  $projectRoot = Split-Path -Parent $root
  Stop-ProjectJavaProcesses
  Write-Host "0) Maven package (once)..."
  & mvn -q -DskipTests clean package
  if ($LASTEXITCODE -ne 0) { throw "mvn package failed (exitCode=$LASTEXITCODE)" }
  Write-Host "0.1) Compile mqtt-server smoke helpers (once)..."
  & mvn -q -pl mqtt-server -DskipTests test-compile
  if ($LASTEXITCODE -ne 0) { throw "mvn test-compile failed (exitCode=$LASTEXITCODE)" }
  $SkipBuild = $true
}

function Run-Case([string]$Name, [string[]]$ExtraArgs) {
  Write-Host ""
  Write-Host "==== CASE: $Name ===="
  $args = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $clusterSmoke,
    "-ClusterName", $ClusterName,
    "-ZkHost", $ZkHost,
    "-ZkPort", $ZkPort,
    "-RedisHost", $RedisHost,
    "-RedisPort", $RedisPort,
    "-RoutePort", $RoutePort,
    "-HttpPort1", $HttpPort1,
    "-HttpPort2", $HttpPort2,
    "-TcpPort1", $TcpPort1,
    "-TcpPort2", $TcpPort2
  )
  if ($SkipBuild) { $args += "-SkipBuild" }
  $args += $ExtraArgs

  & powershell @args
  if ($LASTEXITCODE -ne 0) {
    throw "Case failed: $Name (exitCode=$LASTEXITCODE)"
  }
}

Run-Case -Name "offline_delivery" -ExtraArgs @("-Offline")
Run-Case -Name "ttl_expired_not_delivered" -ExtraArgs @("-Offline", "-OfflineExpired")
Run-Case -Name "ack_cleanup_no_redelivery" -ExtraArgs @("-AckCleanup")
Run-Case -Name "route_offline_store_and_broker_failover" -ExtraArgs @("-RouteStoreFailover")
Run-Case -Name "inflight_resend_with_throttle" -ExtraArgs @("-InflightNoAck")
Run-Case -Name "reliable_queue_limit_trim" -ExtraArgs @("-QueueLimitTest", "-ReliableQueueMaxSize", "5")
Run-Case -Name "reliable_idempotent_msgUUID" -ExtraArgs @("-IdempotentTest", "-ReliableQueueMaxSize", "5")
Run-Case -Name "retain_delivery_on_subscribe" -ExtraArgs @("-RetainTest")
Run-Case -Name "will_message_on_kill" -ExtraArgs @("-WillTest")

Write-Host ""
Write-Host "ALL CASES PASSED"
