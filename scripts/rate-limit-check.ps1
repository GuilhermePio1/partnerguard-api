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

$token = & "$PSScriptRoot\get-token.ps1" -User $User

# All requests go through one curl process on one connection. Starting a
# process per request can take half a second on Windows, slower than the
# bucket refills (about one token per second), so the bucket might never
# empty. Requests after the first 429 are refused as well and take no tokens
# (ADR-0022), so sending them all is harmless.
$urls = @(1..$MaxCalls | ForEach-Object { "$apiUrl/api/v1/claims" })

Write-Host "Sending up to $MaxCalls requests as '$User'..."
# Each response body is followed by a marker line: status|content type|Retry-After.
$lines = @(& $curl -s -H "Authorization: Bearer $token" `
    -w '\n@@response %{http_code}|%{content_type}|%header{retry-after}\n' $urls)

$i = 0
$body = ''
foreach ($line in $lines) {
    if (-not $line.StartsWith('@@response ')) {
        $body += $line
        continue
    }
    $i++
    $fields = $line.Substring('@@response '.Length).Split('|')
    $status = $fields[0]
    $contentType = $fields[1]
    $retryAfter = $fields[2].Trim()

    if ($status -eq '429') {
        Write-Host "Got 429 on request $i"
        $ok = $true

        $seconds = 0
        if ([int]::TryParse($retryAfter, [ref]$seconds) -and $seconds -ge 1) {
            Write-Host "PASS  Retry-After: $seconds"
        } else {
            Write-Host 'FAIL  missing or invalid Retry-After header' -ForegroundColor Red; $ok = $false
        }
        if ($contentType.StartsWith('application/problem+json')) {
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
    $body = ''
}

Write-Host "FAIL  no 429 after $MaxCalls requests" -ForegroundColor Red
exit 1
