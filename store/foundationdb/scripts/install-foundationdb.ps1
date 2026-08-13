Param(
  [string]$Version = $env:FDB_VERSION
)

if (-not $Version -or $Version -eq '') { $Version = '7.3.75' }

$ErrorActionPreference = 'Stop'

function Log($msg) { Write-Host "[install-foundationdb] $msg" }
function Warn($msg) { Write-Warning $msg }
function Die($msg) { Write-Error $msg; exit 1 }

if ($Version -ne '7.3.75') {
  Die "No pinned SHA-256 checksums for FoundationDB version $Version."
}

Die 'Windows CLI bundling is unavailable until FoundationDB release artifacts and SHA-256 checksums are pinned. PATH, package-manager, and cached binaries are intentionally not copied.'
