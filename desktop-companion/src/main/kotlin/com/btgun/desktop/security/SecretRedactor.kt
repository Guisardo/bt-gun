package com.btgun.desktop.security

object SecretRedactor {
    private val rules = listOf(
        Regex(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ) to "<redacted-private-key>",
        Regex("(qr_secret=)[A-Za-z0-9_-]+") to "$1<redacted>",
        Regex("(code=)\\d{6}") to "$1<redacted>",
        Regex("((?:pairing_)?proof=)[A-Za-z0-9_-]+") to "$1<redacted>",
        Regex("((?:X-BT-Gun-)?Pairing-Proof:\\s*)[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE) to "$1<redacted>",
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----") to "<redacted-private-key>",
        Regex("(private_key=)\\S+", RegexOption.IGNORE_CASE) to "$1<redacted>",
        Regex("(?i)\\bstream[_ -]?key\\s*[=:]\\s*\\S+") to "<redacted-stream-key>",
        Regex("(?i)\\bhmac[_ -]?(?:key|material)\\s*[=:]\\s*\\S+") to "<redacted-hmac-material>",
        Regex("(?i)\\bBluetooth address\\s*[=:]\\s*[0-9a-f]{2}(:[0-9a-f]{2}){5}") to "<redacted-bluetooth-address>",
        Regex("(?i)\\b[0-9a-f]{2}(:[0-9a-f]{2}){5}\\b") to "<redacted-bluetooth-address>",
        Regex("(?i)\\b(?:device[_ -]?serial|serial(?:_number)?)\\s*[=:]\\s*\\S+") to "<redacted-device-id>",
        Regex("(?i)\\bAndroid[_ -]?ID\\s*[=:]\\s*\\S+") to "<redacted-device-id>",
        Regex("(?i)\\braw[_ -]?screenshot\\s*[=:]\\s*\\S+") to "<redacted-raw-evidence>",
        Regex("(?i)\\braw[_ -]?log\\s*[=:]\\s*\\S+") to "<redacted-raw-evidence>",
        Regex("(?i)\\.evidence/\\S+\\.(?:png|jpg|jpeg|log|txt)") to "<redacted-raw-evidence>",
    )

    fun redact(value: String): String =
        rules.fold(value) { current, (pattern, replacement) -> pattern.replace(current, replacement) }
}
