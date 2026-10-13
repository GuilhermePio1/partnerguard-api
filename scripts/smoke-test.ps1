<#
.SYNOPSIS
    Calls the running API with each fixture user and checks the status codes
    defined in specs/. Only sends requests that must fail before any write, so
    the seed data stays unchanged when the API is correct.
.EXAMPLE
    .\scripts\smoke-test.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$apiUrl = if ($env:API_URL) { $env:API_URL } else { 'http://localhost:8080' }
# In Windows PowerShell 5.1 "curl" is an alias for Invoke-WebRequest.
$curl = if ($env:OS -eq 'Windows_NT') { 'curl.exe' } else { 'curl' }

# Fixture ids (specs/fixtures.md)
$partnerB  = '10000000-0000-4000-8000-00000000000b'
$contractA = '30000000-0000-4000-8000-00000000000a'
$contractB = '30000000-0000-4000-8000-00000000000b'
$claimA1   = '40000000-0000-4000-8000-00000000a001'
$claimA2   = '40000000-0000-4000-8000-00000000a002'
$claimB1   = '40000000-0000-4000-8000-00000000b001'
$missingId = '40000000-0000-4000-8000-000000000000'

$tmpDir = Join-Path ([IO.Path]::GetTempPath()) ("partnerguard-smoke-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmpDir | Out-Null
$bodyFile    = Join-Path $tmpDir 'body'
$headersFile = Join-Path $tmpDir 'headers'
$requestFile = Join-Path $tmpDir 'request.json'
$utf8NoBom   = New-Object System.Text.UTF8Encoding $false

$script:passed = 0
$script:failed = 0

function Write-Result {
    param([bool]$Ok, [string]$Line)
    if ($Ok) {
        $script:passed++
        Write-Host "PASS  $Line"
    } else {
        $script:failed++
        Write-Host "FAIL  $Line" -ForegroundColor Red
    }
}

function Test-Status {
    param(
        [string]$Id, [string]$Expected, [string]$Method, [string]$Path,
        [string]$Token, [string]$Body
    )
    $curlArgs = @('-s', '-o', $bodyFile, '-D', $headersFile, '-w', '%{http_code}', '-X', $Method)
    if ($Token) { $curlArgs += @('-H', "Authorization: Bearer $Token") }
    if ($Body) {
        # Pass the body through a file: PowerShell 5.1 mangles quotes in native arguments.
        [IO.File]::WriteAllText($requestFile, $Body, $utf8NoBom)
        $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', "@$requestFile")
    }
    $status = (& $curl @curlArgs "$apiUrl$Path") -join ''
    if (-not $status) { $status = '000' }

    $line = '{0,-9} {1,-6} {2,-60}' -f $Id, $Method, $Path
    if ($status -eq $Expected) {
        Write-Result $true "$line $status"
    } else {
        Write-Result $false "$line expected $Expected, got $status"
    }
}

function Test-Condition {
    param([string]$Id, [string]$Description, [bool]$Condition)
    Write-Result $Condition ('{0,-9} {1}' -f $Id, $Description)
}

function Get-FileText([string]$Path) {
    if (Test-Path $Path) { [IO.File]::ReadAllText($Path) } else { '' }
}

try {
    Write-Host 'Getting tokens...'
    $admin   = & "$PSScriptRoot\get-token.ps1" -User admin
    $analyst = & "$PSScriptRoot\get-token.ps1" -User analyst
    $shopA   = & "$PSScriptRoot\get-token.ps1" -User shop-a

    Write-Host "Running checks against $apiUrl"

    $jwtPattern = '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$'
    Test-Condition AUTHN-13 'get-token returns a JWT for admin'   ($admin -cmatch $jwtPattern)
    Test-Condition AUTHN-13 'get-token returns a JWT for analyst' ($analyst -cmatch $jwtPattern)
    Test-Condition AUTHN-13 'get-token returns a JWT for shop-a'  ($shopA -cmatch $jwtPattern)

    # Authentication and public endpoints
    Test-Status AUTHN-01 401 GET  '/api/v1/claims' ''
    Test-Status AUTHZ-14 200 GET  '/actuator/health' ''

    # Partners
    Test-Status AUTHZ-03 200 GET  '/api/v1/partners' $analyst
    Test-Status AUTHZ-03 403 GET  '/api/v1/partners' $shopA
    Test-Status AUTHZ-03 403 POST '/api/v1/partners' $analyst '{"legalName":"Oficina Gama Ltda","taxId":"33.444.555/0001-81"}'
    Test-Status PRT-05   400 POST '/api/v1/partners' $admin   '{"legalName":"Oficina Gama Ltda","taxId":"12.ABC.345/01DE-36"}'
    Test-Status AUTHZ-03 403 GET  '/api/v1/partners/me' $admin
    Test-Status PRT-10   200 GET  '/api/v1/partners/me' $shopA
    $meBody    = Get-FileText $bodyFile
    $meHeaders = Get-FileText $headersFile
    Test-Condition HRD-12 'taxId is masked in /partners/me' ($meBody.Contains('"**.***.***/****-81"'))
    Test-Condition HRD-12 'unmasked taxId is absent' (-not ($meBody.Contains('11222333000181') -or $meBody.Contains('11.222.333/0001-81')))
    Test-Condition HRD-12 'X-Content-Type-Options: nosniff' ($meHeaders -match '(?im)^X-Content-Type-Options:\s*nosniff')

    # Contracts
    Test-Status CTR-04   200 GET  "/api/v1/contracts/$contractA" $shopA
    Test-Status CTR-03   404 GET  "/api/v1/contracts/$contractB" $shopA

    # Claims: reads
    Test-Status CLM-04   200 GET  "/api/v1/claims/$claimA1" $shopA
    Test-Status CLM-03   404 GET  "/api/v1/claims/$claimB1" $shopA
    Test-Status CLM-03   404 GET  "/api/v1/claims/$missingId" $analyst
    Test-Status API-06   400 GET  '/api/v1/claims/not-a-uuid' $analyst

    # Claims: updates that must be refused (no data changes)
    Test-Status CLM-12   403 PATCH "/api/v1/claims/$claimA1" $shopA   '{"status":"APPROVED"}'
    Test-Status CLM-10   403 PATCH "/api/v1/claims/$claimA1" $analyst '{"amount":10}'
    Test-Status CLM-14   404 PATCH "/api/v1/claims/$claimB1" $shopA   '{"description":"x"}'
    Test-Status CLM-15   409 PATCH "/api/v1/claims/$claimA2" $shopA   '{"description":"x"}'
    Test-Status CLM-08   409 PATCH "/api/v1/claims/$claimA2" $analyst '{"status":"UNDER_REVIEW"}'
    Test-Status CLM-17   400 PATCH "/api/v1/claims/$claimA1" $admin   ('{"partnerId":"' + $partnerB + '"}')

    # Policies
    Test-Status POL-01   200 GET  '/api/v1/policies/POL-2026-000001' $analyst
    Test-Status POL-02   403 GET  '/api/v1/policies/POL-2026-000001' $shopA
    Test-Status POL-03   404 GET  '/api/v1/policies/POL-2026-999999' $admin
} finally {
    Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "Passed: $script:passed  Failed: $script:failed"
if ($script:failed -gt 0) { exit 1 }
