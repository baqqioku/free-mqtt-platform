param(
  [ValidateSet("reliability","mvn-test","both")]
  [string]$Mode = "reliability",
  [switch]$SkipBuild,
  [string]$LogFile = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$stateDir = Join-Path $projectRoot ".trae"
if (-not (Test-Path $stateDir)) { New-Item -ItemType Directory -Path $stateDir | Out-Null }
$pidFile = Join-Path $stateDir "test-window.pid"
$logFilePath = $LogFile
if ([string]::IsNullOrWhiteSpace($logFilePath)) {
  $logFilePath = Join-Path $stateDir "test-window.log"
}
$windowTitle = "free-mqtt-tests"

function Stop-PreviousWindow {
  try {
    Get-Process -Name "powershell" -ErrorAction SilentlyContinue |
      Where-Object { $_.MainWindowTitle -eq $windowTitle } |
      Stop-Process -Force -ErrorAction SilentlyContinue
  } catch {
  }

  if (Test-Path $pidFile) {
    try {
      $pid = (Get-Content $pidFile -Raw).Trim()
      if ($pid) {
        $oldPid = [int]$pid
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 300
        try {
          $p = Get-Process -Id $oldPid -ErrorAction Stop
          if ($p) {
            Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
          }
        } catch {
        }
      }
    } catch {
    }
    try { Remove-Item $pidFile -Force -ErrorAction SilentlyContinue } catch {}
  }
}

function Start-TestWindow {
  param([string]$commandLine, [string]$logFile)

  Stop-PreviousWindow

  $wrapped = @"
& {
  `$host.UI.RawUI.WindowTitle = '$windowTitle'
  Set-Location '$projectRoot'
  Start-Transcript -Path '$logFile' -Force | Out-Null
  try {
    $commandLine
    Write-Host 'DONE'
  } catch {
    Write-Host 'FAILED'
    Write-Host (`$_.ToString())
  } finally {
    try { Stop-Transcript | Out-Null } catch {}
  }
}
"@
  $args = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-NoExit",
    "-Command", $wrapped
  )
  $p = Start-Process -FilePath "powershell" -ArgumentList $args -WindowStyle Normal -PassThru
  Set-Content -Path $pidFile -Value $p.Id -Encoding ASCII
  Write-Host ("Started test window PID={0}" -f $p.Id)
  Write-Host ("Log: {0}" -f $logFile)
}

$cmds = New-Object System.Collections.Generic.List[string]
if ($Mode -eq "mvn-test" -or $Mode -eq "both") {
  $cmds.Add("mvn -q test; if (`$LASTEXITCODE -ne 0) { throw 'mvn test failed' }")
}
if ($Mode -eq "reliability" -or $Mode -eq "both") {
  $suite = ".\scripts\reliability-suite.ps1"
  if ($SkipBuild) {
    $cmds.Add("$suite -SkipBuild; if (`$LASTEXITCODE -ne 0) { throw 'reliability-suite failed' }")
  } else {
    $cmds.Add("$suite; if (`$LASTEXITCODE -ne 0) { throw 'reliability-suite failed' }")
  }
}

$commandLine = ($cmds -join " ; ")
Start-TestWindow -commandLine $commandLine -logFile $logFilePath

