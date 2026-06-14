[CmdletBinding()]
param(
    [string]$OutputDirectory = ".\evidence\phase6",
    [string]$PackageRoot = "",
    [string]$Note = ""
)

$ErrorActionPreference = "Stop"

function New-ProofDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
    return (Resolve-Path $Path).Path
}

function Invoke-Capture {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )

    $target = Join-Path $script:OutDir "$Name.txt"
    try {
        $result = & $Command 2>&1 | Out-String
        Set-Content -Path $target -Value $result -Encoding UTF8
        return [pscustomobject]@{
            name = $Name
            status = "captured"
            path = $target
            error = $null
        }
    } catch {
        $message = $_.Exception.Message
        Set-Content -Path $target -Value $message -Encoding UTF8
        return [pscustomobject]@{
            name = $Name
            status = "error"
            path = $target
            error = $message
        }
    }
}

function Get-FileHashRecord {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path $Path -PathType Leaf)) {
        return $null
    }

    $hash = Get-FileHash -Algorithm SHA256 -Path $Path
    return [pscustomobject]@{
        file = Split-Path -Leaf $Path
        sha256 = $hash.Hash.ToLowerInvariant()
    }
}

$script:OutDir = New-ProofDirectory -Path $OutputDirectory
$manifestPath = Join-Path $script:OutDir "phase6-windows-target-cli-evidence.json"
$script:BtGunHidIdentityPattern = "VID_18D1&PID_9400|VID_1209&PID_B706"

$captures = @()
$captures += Invoke-Capture -Name "bcdedit-enum" -Command { & bcdedit /enum }
$captures += Invoke-Capture -Name "pnputil-drivers-btgun" -Command { & pnputil /enum-drivers | Select-String -Pattern "btgun|BtGun|BTGUN" -Context 3,6 }
$captures += Invoke-Capture -Name "pnputil-devices-btgun" -Command { & pnputil /enum-devices /connected | Select-String -Pattern "BT Gun|BTGun|BTGUN|Root\\BTGunVJoy|ROOT\\BTGUNVJOY|$script:BtGunHidIdentityPattern" -Context 2,6 }
$captures += Invoke-Capture -Name "pnpdevice-btgun" -Command {
    Get-PnpDevice -PresentOnly |
        Where-Object {
            $_.FriendlyName -like "*BT Gun*" -or
            $_.FriendlyName -like "*BTGun*" -or
            $_.InstanceId -like "*BTGUNVJOY*" -or
            $_.InstanceId -like "*BTGunVJoy*" -or
            $_.InstanceId -match $script:BtGunHidIdentityPattern
        } |
        Format-List *
}
$captures += Invoke-Capture -Name "hid-game-controller-pnp" -Command {
    Get-PnpDevice -PresentOnly |
        Where-Object {
            $_.Class -in @("HIDClass", "MEDIA", "System") -and (
                $_.FriendlyName -like "*BT Gun*" -or
                $_.FriendlyName -like "*BTGun*" -or
                $_.InstanceId -like "*BTGUNVJOY*" -or
                $_.InstanceId -match $script:BtGunHidIdentityPattern
            )
        } |
        Select-Object Status, Class, FriendlyName, InstanceId |
        Format-Table -AutoSize
}
$captures += Invoke-Capture -Name "directinput-oem-name-btgun" -Command {
    @("VID_18D1&PID_9400", "VID_1209&PID_B706") |
        ForEach-Object {
            Get-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\MediaProperties\PrivateProperties\Joystick\OEM\$_" -ErrorAction SilentlyContinue
        } |
        Select-Object PSPath, OEMName |
        Format-List *
}
$captures += Invoke-Capture -Name "signed-driver-btgun" -Command {
    Get-CimInstance Win32_PnPSignedDriver |
        Where-Object {
            $_.DeviceName -like "*BT Gun*" -or
            $_.DeviceName -like "*BTGun*" -or
            $_.InfName -like "*btgun*" -or
            $_.HardwareID -match "BTGUNVJOY|BTGunVJoy|$script:BtGunHidIdentityPattern"
        } |
        Select-Object DeviceName, DriverVersion, DriverProviderName, InfName, IsSigned, Signer, HardwareID |
        Format-List *
}

$packageFiles = @()
if (-not [string]::IsNullOrWhiteSpace($PackageRoot) -and (Test-Path $PackageRoot)) {
    $resolvedPackageRoot = (Resolve-Path $PackageRoot).Path
    foreach ($name in @("BtGunVJoy.sys", "btgunvjoy.inf", "btgunvjoy.cat", "BtGunVJoyIoctl.h", "btgun-driver-bridge.exe", "btgun-hid-output-sender.exe", "btgun-devnode.exe", "build-metadata.json")) {
        $match = Get-ChildItem -Path $resolvedPackageRoot -Recurse -File -Filter $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $match) {
            $packageFiles += Get-FileHashRecord -Path $match.FullName
        }
    }
}

$summary = [pscustomobject]@{
    schema = "btgun.phase6.windows_target_cli_evidence.v1"
    capture_id = "phase6-pnp-hid-cli"
    target_host = $env:COMPUTERNAME
    collected_at_utc = (Get-Date).ToUniversalTime().ToString("o")
    note = $Note
    collector = "Collect-BtGunVJoyEvidence.ps1"
    read_only = $true
    target_toolchain_dependencies = @()
    commands = $captures
    package_hashes = $packageFiles
    next_required_manual_steps = @(
        "Run control joy.cpl and capture visual/user confirmation.",
        "Run live Android/gun input proof.",
        "Try joy.cpl output first, then fallback sender only if limitation is documented.",
        "Run Redact-BtGunVJoyEvidence.ps1 before committing manifest updates."
    )
}

$summary | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding UTF8
Write-Host "Wrote read-only Phase 6 CLI evidence to $script:OutDir"
Write-Host "USER APPROVAL REQUIRED before install, devnode, rollback, reboot, joy.cpl GUI proof, or output haptic proof commands."
