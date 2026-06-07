package com.btgun.desktop.security

object SecretRedactor {
    private val rules = listOf(
        Regex("(qr_secret=)[A-Za-z0-9_-]+") to "$1<redacted>",
        Regex("(code=)\\d{6}") to "$1<redacted>",
        Regex("((?:pairing_)?proof=)[A-Za-z0-9_-]+") to "$1<redacted>",
        Regex("(private_key=).*", RegexOption.IGNORE_CASE) to "$1<redacted>",
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----") to "<redacted-private-key>",
    )

    fun redact(value: String): String =
        rules.fold(value) { current, (pattern, replacement) -> pattern.replace(current, replacement) }
}
