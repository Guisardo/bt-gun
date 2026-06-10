[CmdletBinding()]
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
    [Parameter(Mandatory = $true)][string]$DriverPackageRoot,
    [Parameter(Mandatory = $true)][string]$ToolsOutputRoot,
    [Parameter(Mandatory = $true)][string]$PackageOutputRoot,
    [string]$Configuration = "Release",
    [string]$Platform = "x64",
    [string]$GitSha = "",
    [string]$GitRef = "",
    [string]$MsBuildVersion = "",
    [string]$WdkBinDir = ""
)

$ErrorActionPreference = "Stop"

function Copy-RequiredFile {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )
    if (-not (Test-Path $Source -PathType Leaf)) {
        throw "Required package input missing: $Source"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
    Copy-Item -Path $Source -Destination $Destination -Force
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLowerInvariant()
}

$repo = (Resolve-Path $RepoRoot).Path
$driverRoot = (Resolve-Path $DriverPackageRoot).Path
$toolsRoot = (Resolve-Path $ToolsOutputRoot).Path

if (Test-Path $PackageOutputRoot) {
    Remove-Item $PackageOutputRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $PackageOutputRoot | Out-Null

$packageRoot = (Resolve-Path $PackageOutputRoot).Path
$driverOut = Join-Path $packageRoot "driver"
$toolsOut = Join-Path $packageRoot "tools"
$proofOut = Join-Path $toolsOut "proof"
$scriptsOut = Join-Path $packageRoot "scripts"
$includeOut = Join-Path $packageRoot "include"

Copy-RequiredFile (Join-Path $driverRoot "BtGunVJoy.sys") (Join-Path $driverOut "BtGunVJoy.sys")
Copy-RequiredFile (Join-Path $driverRoot "btgunvjoy.inf") (Join-Path $driverOut "btgunvjoy.inf")
Copy-RequiredFile (Join-Path $driverRoot "btgunvjoy.cat") (Join-Path $driverOut "btgunvjoy.cat")
Copy-RequiredFile (Join-Path $repo "windows\btgun-vjoy\include\BtGunVJoyIoctl.h") (Join-Path $includeOut "BtGunVJoyIoctl.h")
Copy-RequiredFile (Join-Path $toolsRoot "btgun-driver-bridge.exe") (Join-Path $toolsOut "btgun-driver-bridge.exe")
Copy-RequiredFile (Join-Path $toolsRoot "btgun-hid-output-sender.exe") (Join-Path $toolsOut "btgun-hid-output-sender.exe")
Copy-RequiredFile (Join-Path $toolsRoot "btgun-devnode.exe") (Join-Path $toolsOut "btgun-devnode.exe")
Copy-RequiredFile (Join-Path $repo "windows\btgun-vjoy\tools\proof\Collect-BtGunVJoyEvidence.ps1") (Join-Path $proofOut "Collect-BtGunVJoyEvidence.ps1")
Copy-RequiredFile (Join-Path $repo "windows\btgun-vjoy\tools\proof\Redact-BtGunVJoyEvidence.ps1") (Join-Path $proofOut "Redact-BtGunVJoyEvidence.ps1")
Copy-RequiredFile (Join-Path $repo "windows\btgun-vjoy\package\Install-BtGunVJoy.ps1") (Join-Path $scriptsOut "Install-BtGunVJoy.ps1")
Copy-RequiredFile (Join-Path $repo "windows\btgun-vjoy\package\Rollback-BtGunVJoy.ps1") (Join-Path $scriptsOut "Rollback-BtGunVJoy.ps1")

$files = Get-ChildItem $packageRoot -Recurse -File |
    Where-Object { $_.Name -ne "build-metadata.json" } |
    Sort-Object FullName |
    ForEach-Object {
        [pscustomobject]@{
            path = $_.FullName.Substring($packageRoot.Length + 1).Replace("\", "/")
            sha256 = Get-FileSha256 $_.FullName
            bytes = $_.Length
        }
    }

$metadata = [ordered]@{
    artifact = "btgun-vjoy-windows-x64-testsigned"
    createdUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    gitSha = $GitSha
    gitRef = $GitRef
    configuration = $Configuration
    platform = $Platform
    msbuildVersion = $MsBuildVersion
    wdkBinDir = $WdkBinDir
    driverProject = "windows/btgun-vjoy/driver/BtGunVJoy.vcxproj"
    inf = "driver/btgunvjoy.inf"
    hardwareId = "Root\BTGunVJoy"
    hidIdentity = @{
        vendorId = "VID_1209"
        productId = "PID_B706"
        versionNumber = "0x0602"
        directInputOemKey = "HKLM:\SYSTEM\CurrentControlSet\Control\MediaProperties\PrivateProperties\Joystick\OEM\VID_1209&PID_B706"
        displayName = "BT Gun VJoy"
    }
    devnodeTool = "tools/btgun-devnode.exe"
    proofCollector = "tools/proof/Collect-BtGunVJoyEvidence.ps1"
    reportIds = @{
        input = 1
        output = 2
    }
    signing = @{
        mode = "test-signed"
        privateMaterialCommitted = $false
        secretNames = @(
            "BTGUN_WINDOWS_TEST_CERT_PFX_BASE64",
            "BTGUN_WINDOWS_TEST_CERT_PASSWORD"
        )
    }
    files = $files
}

$metadataPath = Join-Path $packageRoot "build-metadata.json"
$metadata | ConvertTo-Json -Depth 8 | Set-Content -Path $metadataPath -Encoding UTF8

$forbidden = Get-ChildItem $packageRoot -Recurse -Include *.pfx,*.p12,*.key,id_rsa -File
if ($forbidden) {
    throw "Package contains private key material: $($forbidden.FullName -join ', ')"
}

Write-Host "Packaged BT Gun VJoy artifact at $packageRoot"
Write-Host "Included files:"
Get-ChildItem $packageRoot -Recurse -File | ForEach-Object {
    Write-Host " - $($_.FullName.Substring($packageRoot.Length + 1).Replace('\', '/'))"
}
