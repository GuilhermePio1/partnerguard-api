<#
.SYNOPSIS
    Sends GET /api/v1/claims as one user until the API answers 429, then checks
    the Retry-After header and the Problem Details body (spec RL-10).
    This empties the user's bucket: wait about a minute before reusing the user.
.EXAMPLE
    .\scripts\rate-limit-check.ps1 -User shop-a
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$User = 'shop-a',

    [int]$MaxCalls = $(if ($env:MAX_CALLS) { [int]$env:MAX_CALLS } else { 100 })
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$apiUrl = if ($env:API_URL) { $env:API_URL } else { 'http://localhost:8080' }
# In Windows PowerShell 5.1 "curl" is an alias for Invoke-WebRequest.
$curl = if ($env:OS -eq 'Windows_NT') { 'curl.exe' } else { 'curl' }

$tmpDir = Join-Path ([IO.Path]::GetTempPath()) ("partnerguard-ratelimit-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmpDir | Out-Null
$bodyFile    = Join-Path $tmpDir 'body'
$headersFile = Join-Path $tmpDir 'headers'

try {
    $token = & "$PSScriptRoot\get-token.ps1" -User $User

    Write-Host "Sending up to $MaxCalls requests as '$User'..."
    for ($i = 1; $i -le $MaxCalls; $i++) {
        $status = (& $curl -s -o $bodyFile -D $headersFile -w '%{http_code}' `
            -H "Authorization: Bearer $token" "$apiUrl/api/v1/claims") -join ''

        if ($status -eq '429') {
            Write-Host "Got 429 on request $i"
            $headers = [IO.File]::ReadAllText($headersFile)
            $body    = [IO.File]::ReadAllText($bodyFile)
            $ok = $true

            $match = [regex]::Match($headers, '(?im)^Retry-After:\s*(\d+)')
            if ($match.Success -and [int]$match.Groups[1].Value -ge 1) {
                Write-Host "PASS  Retry-After: $($match.Groups[1].Value)"
            } else {
                Write-Host 'FAIL  missing or invalid Retry-After header' -ForegroundColor Red; $ok = $false
            }
            if ($headers -match '(?im)^Content-Type:\s*application/problem\+json') {
                Write-Host 'PASS  Content-Type: application/problem+json'
            } else {
                Write-Host 'FAIL  Content-Type is not application/problem+json' -ForegroundColor Red; $ok = $false
            }
            if ($body.Contains('rate-limit-exceeded')) {
                Write-Host 'PASS  Problem type rate-limit-exceeded'
            } else {
                Write-Host 'FAIL  body has no rate-limit-exceeded problem type' -ForegroundColor Red; $ok = $false
            }
            if ($ok) { exit 0 } else { exit 1 }
        }

        if ($status -ne '200') {
            Write-Host "FAIL  request $i returned $status before any 429" -ForegroundColor Red
            exit 1
        }
    }

    Write-Host "FAIL  no 429 after $MaxCalls requests" -ForegroundColor Red
    exit 1
} finally {
    Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
}
