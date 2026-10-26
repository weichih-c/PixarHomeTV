# Build the door hall launcher and install it on the TV, with HomeRedirectService as the only accessibility
# service (it replaces cleanYourTV's key-filtering HomeKeyService, so held volume keys ramp again).
# Uses cleanYourTV's ADB client (tools\tv.py, tools\b64push.py and its venv).
#
# Usage: tools\install.ps1 -TvHost <tv ip> [-Cyt <path to cleanYourTV>]
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Cyt = ''
)
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 leaves $PSScriptRoot empty inside param defaults, so resolve the default here.
if (-not $Cyt) { $Cyt = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'cleanYourTV' }
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$py = Join-Path $Cyt 'tools\venv\Scripts\python.exe'
$tv = Join-Path $Cyt 'tools\tv.py'
if (-not (Test-Path $py)) { throw "cleanYourTV venv not found under $Cyt" }

Set-Location (Split-Path -Parent $PSScriptRoot)
.\gradlew.bat assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

& $py -I (Join-Path $Cyt 'tools\b64push.py') app\build\outputs\apk\release\app-release.apk /data/local/tmp/livingroom.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }
$out = & $py -I $tv 'pm install -r /data/local/tmp/livingroom.apk; rm -f /data/local/tmp/livingroom.apk'
Write-Host $out
if ("$out" -notmatch 'Success') { throw 'pm install failed' }

$svc = 'com.livingroom.home/com.livingroom.home.HomeRedirectService'
@(
    'cmd package set-home-activity --user 0 com.livingroom.home/.MainActivity',
    'cmd package compile -m speed -f com.livingroom.home',
    # Clear first so the list is a change and the service binds even if it was listed before.
    "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services $svc; settings put secure accessibility_enabled 1",
    'am start -W -n com.livingroom.home/.MainActivity'
) | & $py -I $tv --batch
Write-Host 'Installed. Press Home on the remote: the stock home should hand over to the door hall.'

