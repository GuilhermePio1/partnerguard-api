<#
.SYNOPSIS
    Starts PostgreSQL and Keycloak and waits until both are ready.
.EXAMPLE
    .\scripts\env-up.ps1
#>
[CmdletBinding()]
param(
    [int]$TimeoutSeconds = $(if ($env:TIMEOUT_SECONDS) { [int]$env:TIMEOUT_SECONDS } else { 180 })
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$keycloakUrl  = if ($env:KEYCLOAK_URL)  { $env:KEYCLOAK_URL }  else { 'http://localhost:8180' }
$realm        = if ($env:REALM)         { $env:REALM }         else { 'partnerguard' }
$postgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'partnerguard' }

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

function Wait-Until {
    param([string]$Name, [scriptblock]$Probe)
    Write-Host -NoNewline "Waiting for $Name"
    while (-not (& $Probe)) {
        if ((Get-Date) -ge $deadline) {
            Write-Host ''
            throw "$Name not ready after ${TimeoutSeconds}s"
        }
        Write-Host -NoNewline '.'
        Start-Sleep -Seconds 2
    }
    Write-Host ' ready'
}

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }

    Wait-Until 'PostgreSQL' {
        # Redirecting native stderr under 'Stop' throws in Windows PowerShell 5.1.
        $ErrorActionPreference = 'Continue'
        docker compose exec -T postgres pg_isready -U $postgresUser *> $null
        $LASTEXITCODE -eq 0
    }

    Wait-Until "Keycloak realm '$realm'" {
        try {
            Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 `
                -Uri "$keycloakUrl/realms/$realm/.well-known/openid-configuration" | Out-Null
            $true
        } catch {
            $false
        }
    }

    Write-Host 'Environment is up. Start the API with: .\mvnw.cmd spring-boot:run'
} finally {
    Pop-Location
}
