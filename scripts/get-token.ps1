<#
.SYNOPSIS
    Prints an access token for a local fixture user (see specs/fixtures.md).
    The password grant is for local development only.
.EXAMPLE
    $token = .\scripts\get-token.ps1 -User shop-a
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$User,

    [Parameter(Position = 1)]
    [string]$Password
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$keycloakUrl = if ($env:KEYCLOAK_URL) { $env:KEYCLOAK_URL } else { 'http://localhost:8180' }
$realm       = if ($env:REALM)        { $env:REALM }        else { 'partnerguard' }
$clientId    = if ($env:CLIENT_ID)    { $env:CLIENT_ID }    else { 'partnerguard-cli' }
if (-not $Password) { $Password = $User }

$form = @{
    grant_type = 'password'
    client_id  = $clientId
    username   = $User
    password   = $Password
}

try {
    $response = Invoke-RestMethod -Method Post `
        -Uri "$keycloakUrl/realms/$realm/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body $form
} catch {
    throw "Token request for '$User' failed: $($_.Exception.Message)"
}

if (-not $response.access_token) {
    throw 'No access_token in Keycloak response'
}

Write-Output $response.access_token
