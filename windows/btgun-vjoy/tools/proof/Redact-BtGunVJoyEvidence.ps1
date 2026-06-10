[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputPath
)

$ErrorActionPreference = "Stop"

$resolvedInput = Resolve-Path $InputPath
if (-not (Test-Path $OutputPath)) {
    New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
}
$resolvedOutput = (Resolve-Path $OutputPath).Path

$redactions = [ordered]@{
    "private-key-block" = '-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----'
    "cert-password-assignment" = '(?i)(cert(ificate)?[-_ ]?(password|pass)|pfx[-_ ]?(password|pass)|BTGUN_WINDOWS_TEST_CERT_PASSWORD)\s*[:=]\s*\S+'
    "qr-secret" = '(?i)(qr[-_ ]?(secret|payload)|manual[-_ ]?code|pairing[-_ ]?(secret|code)|one[-_ ]?(time[-_ ]?)?secret)\s*[:=]\s*\S+'
    "proof-value" = '(?i)(proof[-_ ]?(value|token)?|proof)\s*[:=]\s*\S+'
    "stream-key" = '(?i)(stream[-_ ]?key|hmac[-_ ]?key|session[-_ ]?key)\s*[:=]\s*\S+'
    "bluetooth-address" = '(?i)\b([0-9A-F]{2}:){5}[0-9A-F]{2}\b'
    "windows-device-instance" = '(?i)\b(ROOT|HID|USB|BTHENUM|SWD)\\[^\s"'']+'
    "screenshot-path" = '(?i)[A-Z]:\\[^\r\n"'']*\.(png|jpg|jpeg|bmp)|/[^\r\n"'']*\.(png|jpg|jpeg|bmp)'
    "local-user-path" = '(?i)C:\\Users\\[^\\\r\n"'']+|/Users/[^/\r\n"'']+'
}

$scanHits = New-Object System.Collections.Generic.List[object]

Get-ChildItem -Path $resolvedInput -Recurse -File | ForEach-Object {
    $relative = Resolve-Path -Path $_.FullName -Relative
    $dest = Join-Path $resolvedOutput $_.Name

    if ($_.Extension -match "^\.(png|jpg|jpeg|bmp)$") {
        Set-Content -Path $dest -Value "[REDACTED:screenshot-file]" -Encoding UTF8
        $scanHits.Add([pscustomobject]@{
            file = $relative
            rule = "screenshot-file"
            count = 1
        })
        return
    }

    $text = Get-Content -Path $_.FullName -Raw -ErrorAction Stop

    foreach ($entry in $redactions.GetEnumerator()) {
        $matches = [regex]::Matches($text, $entry.Value)
        if ($matches.Count -gt 0) {
            $scanHits.Add([pscustomobject]@{
                file = $relative
                rule = $entry.Key
                count = $matches.Count
            })
            $text = [regex]::Replace($text, $entry.Value, "[REDACTED:$($entry.Key)]")
        }
    }

    Set-Content -Path $dest -Value $text -Encoding UTF8
}

$scan = [pscustomobject]@{
    schema = "btgun.phase6.redaction_scan.v1"
    capture_id = "phase6-redaction-scan"
    scanned_at_utc = (Get-Date).ToUniversalTime().ToString("o")
    input_path = "[REDACTED:local-path]"
    output_path = "[REDACTED:local-path]"
    status = "redacted"
    hit_count = $scanHits.Count
    hits = $scanHits
    committed_manifest_rule = "Commit sanitized status rows and redacted evidence refs only. Do not commit raw logs or screenshots."
}

$scan | ConvertTo-Json -Depth 6 | Set-Content -Path (Join-Path $resolvedOutput "phase6-redaction-scan.json") -Encoding UTF8
Write-Host "Wrote redacted evidence to $resolvedOutput"
