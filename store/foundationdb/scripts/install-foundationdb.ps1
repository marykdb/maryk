Param(
  [string]$Version = $env:FDB_VERSION
)

if (-not $Version -or $Version -eq '') { $Version = '7.4.3' }

$ErrorActionPreference = 'Stop'

function Log($msg) { Write-Host "[install-foundationdb] $msg" }
function Warn($msg) { Write-Warning $msg }
function Die($msg) { Write-Error $msg; exit 1 }

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$Root = Resolve-Path (Join-Path $ScriptDir "..\..\..")
$BinDir = Join-Path $Root 'store\foundationdb\bin'
New-Item -ItemType Directory -Force -Path $BinDir | Out-Null

$FdbServer = Join-Path $BinDir 'fdbserver.exe'
$FdbCli = Join-Path $BinDir 'fdbcli.exe'

if (Test-Path $FdbServer) {
  Log "fdbserver already present in $BinDir"
  exit 0
}

# If already on PATH, copy/symlink it
$onPath = (Get-Command fdbserver -ErrorAction SilentlyContinue)
if ($onPath) {
  Copy-Item $onPath.Path $FdbServer -Force
  Log "Copied fdbserver from PATH: $($onPath.Path)"
  $cli = (Get-Command fdbcli -ErrorAction SilentlyContinue)
  if ($cli) { Copy-Item $cli.Path $FdbCli -Force }
  exit 0
}

# Try Chocolatey
$choco = (Get-Command choco -ErrorAction SilentlyContinue)
if ($choco) {
  Log "Installing FoundationDB via Chocolatey"
  choco install foundationdb --version $Version -y | Out-Null
}
else {
  # Try winget
  $winget = (Get-Command winget -ErrorAction SilentlyContinue)
  if ($winget) {
    Log "Installing FoundationDB via winget"
    winget install --id FoundationDB.FoundationDB --accept-package-agreements --accept-source-agreements --silent | Out-Null
  } else {
    Warn "Neither Chocolatey nor winget found. Please install FoundationDB manually."
  }
}

# Look for typical install location
$possible = @(
  'C:\Program Files\FoundationDB\bin\fdbserver.exe',
  'C:\Program Files (x86)\FoundationDB\bin\fdbserver.exe'
)
foreach ($p in $possible) {
  if (Test-Path $p) {
    Copy-Item $p $FdbServer -Force
    Log "Copied fdbserver from $p"
  }
}

if (-not (Test-Path $FdbServer)) {
  Die 'fdbserver not installed. Please install FoundationDB and re-run.'
}

Log 'FoundationDB installation complete'
