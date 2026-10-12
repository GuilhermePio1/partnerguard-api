<#
.SYNOPSIS
    Stops the local stack. With -Volumes, also deletes the data (resets the seed).
.EXAMPLE
    .\scripts\env-down.ps1 -Volumes
#>
[CmdletBinding()]
param(
    [switch]$Volumes
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if ($Volumes) {
        docker compose down --volumes
    } else {
        docker compose down
    }
    if ($LASTEXITCODE -ne 0) { throw 'docker compose down failed' }
} finally {
    Pop-Location
}
