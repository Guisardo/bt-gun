package com.btgun.desktop.security

object SecretRedactor {
    private val rules = listOf(
        Regex("(qr_secret=)[A-Za-z0-9_-]+") to "$1<redacted>",
        Regex("(code=)\\d{6}") to "$1<redacted>",
        Regex("(proof=)[A-Fa-f0-9]+") to "$1<redacted>",
        Regex("(private_key=)[A-Za-z0-9_+/=-]+") to "$1<redacted>",
    )

    fun redact(value: String): String =
        rules.fold(value) { current, (pattern, replacement) -> pattern.replace(current, replacement) }
}
