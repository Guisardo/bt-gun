[CmdletBinding()]
param(
    [switch]$ApproveDeviceRemoval,
    [switch]$ApproveDriverDelete,
    [switch]$ApproveTestSigningOff
)

$ErrorActionPreference = "Stop"

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
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

Write-Host "BT Gun VJoy rollback"
Write-Host "Current test-signing status:"
& bcdedit /enum

Write-Host "Current matching devices:"
& pnputil /enum-devices /instanceid "ROOT\BTGUNVJOY\*" 2>$null

Invoke-ApprovedCommand `
    -Message "Remove Root\BTGunVJoy device instance with pnputil /remove-device when present." `
    -Approved $ApproveDeviceRemoval.IsPresent `
    -Command {
        & pnputil /remove-device "ROOT\BTGUNVJOY\*"
    }

$matchingDrivers = (& pnputil /enum-drivers) -join "`n"
$publishedNames = [regex]::Matches(
    $matchingDrivers,
    "Published Name\s*:\s*(oem\d+\.inf).*?Original Name\s*:\s*btgunvjoy\.inf",
    [Text.RegularExpressions.RegexOptions]::Singleline -bor [Text.RegularExpressions.RegexOptions]::IgnoreCase
) | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique

if ($publishedNames.Count -eq 0) {
    Write-Host "No published btgunvjoy.inf driver package found."
} else {
    foreach ($publishedName in $publishedNames) {
        Invoke-ApprovedCommand `
            -Message "Delete Windows driver package $publishedName with pnputil /delete-driver /uninstall /force." `
            -Approved $ApproveDriverDelete.IsPresent `
            -Command {
                & pnputil /delete-driver $publishedName /uninstall /force
            }
    }
}

Invoke-ApprovedCommand `
    -Message "Disable Windows test-signing with 'bcdedit /set testsigning off'. This can require reboot; this script will not reboot." `
    -Approved $ApproveTestSigningOff.IsPresent `
    -Command {
        & bcdedit /set testsigning off
        Write-Host "If Windows reports a reboot is required, reboot only after user approval."
    }

Write-Host "Rollback proof commands:"
Write-Host "  pnputil /enum-devices /instanceid `"ROOT\\BTGUNVJOY\\*`""
Write-Host "  pnputil /enum-drivers | findstr /i btgun"
Write-Host "  bcdedit /enum"
