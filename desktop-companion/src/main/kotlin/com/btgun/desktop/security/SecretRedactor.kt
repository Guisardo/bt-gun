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
    )

    fun redact(value: String): String =
        rules.fold(value) { current, (pattern, replacement) -> pattern.replace(current, replacement) }
}
