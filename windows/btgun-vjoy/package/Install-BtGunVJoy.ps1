[CmdletBinding()]
param(
    [string]$PackageRoot = "",
    [string]$PublicCertificatePath = "",
    [switch]$ApproveTestSigning,
    [switch]$ApproveCertificateImport,
    [switch]$ApproveDriverInstall,
    [switch]$ApproveDevnode
)

$ErrorActionPreference = "Stop"

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Resolve-PackageRoot {
    if (-not [string]::IsNullOrWhiteSpace($PackageRoot)) {
        return (Resolve-Path $PackageRoot).Path
    }

    $candidates = @(
        (Join-Path $PSScriptRoot "..\driver"),
        $PSScriptRoot
    )
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "btgunvjoy.inf")) {
            return (Resolve-Path $candidate).Path
        }
    }
    throw "btgunvjoy.inf not found. Run from artifact scripts directory or pass -PackageRoot."
}

function Invoke-ApprovedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Message,
        [Parameter(Mandatory = $true)][bool]$Approved,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )
    Write-Host "USER APPROVAL REQUIRED: $Message"
    if (-not $Approved) {
        Write-Host "Skipped. Re-run with the matching -Approve* switch after approval."
        return
    }
    & $Command
}

if (-not (Test-IsAdministrator)) {
    throw "Administrator PowerShell is required."
}

$driverRoot = Resolve-PackageRoot
$infPath = Join-Path $driverRoot "btgunvjoy.inf"
if (-not (Test-Path $infPath -PathType Leaf)) {
    throw "Missing INF: $infPath"
}

Write-Host "BT Gun VJoy install package: $driverRoot"
Write-Host "Current test-signing status:"
& bcdedit /enum

Invoke-ApprovedCommand `
    -Message "Enable Windows test-signing with 'bcdedit /set testsigning on'. This can require reboot; this script will not reboot." `
    -Approved $ApproveTestSigning.IsPresent `
    -Command {
        & bcdedit /set testsigning on
        Write-Host "If Windows reports a reboot is required, reboot only after user approval."
    }

if (-not [string]::IsNullOrWhiteSpace($PublicCertificatePath)) {
    $certPath = (Resolve-Path $PublicCertificatePath).Path
    Invoke-ApprovedCommand `
        -Message "Import public test certificate into LocalMachine Root and TrustedPublisher stores: $certPath" `
        -Approved $ApproveCertificateImport.IsPresent `
        -Command {
            Import-Certificate -FilePath $certPath -CertStoreLocation Cert:\LocalMachine\Root | Out-Null
            Import-Certificate -FilePath $certPath -CertStoreLocation Cert:\LocalMachine\TrustedPublisher | Out-Null
        }
}

Invoke-ApprovedCommand `
    -Message "Add and install btgunvjoy.inf using pnputil /add-driver. No WDK, Visual Studio, MSBuild, Git, or build tools are installed." `
    -Approved $ApproveDriverInstall.IsPresent `
    -Command {
        & pnputil /add-driver $infPath /install
    }

$artifactRoot = Split-Path -Parent $driverRoot
$devnodeTool = Join-Path $artifactRoot "tools\btgun-devnode.exe"
if (Test-Path $devnodeTool -PathType Leaf) {
    Invoke-ApprovedCommand `
        -Message "Create or verify Root\BTGunVJoy devnode with packaged btgun-devnode.exe, then ask PnP to rescan devices." `
        -Approved $ApproveDevnode.IsPresent `
        -Command {
            & $devnodeTool --inf $infPath --hardware-id "Root\BTGunVJoy" --device-name "BT Gun VJoy"
            if ($LASTEXITCODE -ne 0) {
                throw "btgun-devnode.exe failed with exit code $LASTEXITCODE."
            }
            & pnputil /scan-devices
            if ($LASTEXITCODE -ne 0) {
                throw "pnputil /scan-devices failed with exit code $LASTEXITCODE."
            }
        }
} else {
    if ($ApproveDevnode.IsPresent) {
        throw "Missing packaged tool: $devnodeTool. Rebuild/download the CI artifact before devnode proof."
    }
    Write-Host "No packaged btgun-devnode.exe found. If pnputil did not bind an existing Root\BTGunVJoy devnode, rebuild/download the CI artifact before final proof."
}

Write-Host "Proof commands:"
Write-Host "  pnputil /enum-drivers | findstr /i btgun"
Write-Host "  pnputil /enum-devices /connected | findstr /i `"BT Gun Root\\BTGunVJoy`""
Write-Host "  control joy.cpl"
Write-Host "  .\tools\btgun-driver-bridge.exe"
Write-Host "  .\tools\btgun-hid-output-sender.exe --strength 192 --duration-ms 120 --ttl-ms 500"
