package com.btgun.host.session

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class PairingPayloadV1(
    val v: Int = 1,
    val sid: String,
    val host: String,
    val port: Int,
    val expiresAtEpochMillis: Long,
    val desktopSpkiSha256: String,
    val desktopNonce: String,
    val qrSecret: String,
) {
    init {
        require(v == VERSION) { "v must be 1" }
        require(sid.isNotBlank()) { "sid must not be blank" }
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in PORT_RANGE) { "port must be between 1 and 65535" }
        require(expiresAtEpochMillis > 0L) { "expiresAtEpochMillis must be positive" }
        require(desktopSpkiSha256.matches(FINGERPRINT_REGEX)) { "desktopSpkiSha256 must be lowercase SHA-256 hex" }
        require(desktopNonce.matches(HEX_ENTROPY_REGEX)) { "desktopNonce must include enough entropy" }
        require(qrSecret.matches(QR_SECRET_REGEX)) { "qrSecret must include enough entropy" }
    }
}

data class ManualPairingPayload(
    val host: String,
    val port: Int,
    val code: String,
    val desktopSpkiSha256Suffix: String,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in PORT_RANGE) { "port must be between 1 and 65535" }
        require(code.matches(MANUAL_CODE_REGEX)) { "code must be six digits" }
        require(desktopSpkiSha256Suffix.matches(FINGERPRINT_SUFFIX_REGEX)) { "desktopSpkiSha256Suffix must be visible" }
    }
}

sealed interface PairingParseResult<out T> {
    data class Valid<T>(val value: T) : PairingParseResult<T>

    data class Invalid(
        val error: PairingPayloadError,
        val message: String,
        val field: String? = null,
        val recoveryAction: PairingRecoveryAction,
    ) : PairingParseResult<Nothing>
}

enum class PairingPayloadError {
    UNSUPPORTED_URI,
    UNSUPPORTED_VERSION,
    MISSING_FIELD,
    MALFORMED_FIELD,
    DUPLICATE_FIELD,
    EXPIRED,
    OVERSIZED,
}

enum class PairingRecoveryAction {
    RESCAN_QR,
    RESCAN_OR_MANUAL_EDIT,
    MANUAL_EDIT,
}

object PairingPayload {
    fun parseQrUri(payload: String, nowEpochMillis: Long): PairingParseResult<PairingPayloadV1> {
        if (payload.length > MAX_QR_PAYLOAD_LENGTH) {
            return invalid(
                error = PairingPayloadError.OVERSIZED,
                message = "Pairing QR is too large. Rescan the QR code.",
                recoveryAction = PairingRecoveryAction.RESCAN_QR,
            )
        }

        val uri = try {
            URI(payload)
        } catch (_: IllegalArgumentException) {
            return invalidUri()
        } catch (_: URISyntaxException) {
            return invalidUri()
        }

        if (uri.scheme != "btgun" || uri.host != "pair") {
            return invalidUri()
        }

        val fields = parseQuery(uri.rawQuery ?: "")
        fields.invalid?.let { return it }

        fun required(name: String): String? =
            fields.values[name]

        val version = requiredInt("v", required("v")) ?: return missingOrMalformed(fields.values, "v", qr = true)
        if (version != VERSION) {
            return invalid(
                error = PairingPayloadError.UNSUPPORTED_VERSION,
                field = "v",
                message = "Unsupported pairing QR version. Rescan the QR code.",
                recoveryAction = PairingRecoveryAction.RESCAN_QR,
            )
        }
        val sid = required("sid")?.trim().takeIf { !it.isNullOrBlank() }
            ?: return missing("sid", qr = true)
        val host = required("host")?.trim().takeIf { !it.isNullOrBlank() }
            ?: return missing("host", qr = true)
        val port = requiredInt("port", required("port")) ?: return missingOrMalformed(fields.values, "port", qr = true)
        val expiresAt = requiredLong("expires_at_epoch_millis", required("expires_at_epoch_millis"))
            ?: return missingOrMalformed(fields.values, "expires_at_epoch_millis", qr = true)
        val fingerprint = required("desktop_spki_sha256")?.lowercase(Locale.US)
            ?.takeIf { it.matches(FINGERPRINT_REGEX) }
            ?: return missingOrMalformed(fields.values, "desktop_spki_sha256", qr = true)
        val nonce = required("desktop_nonce")?.lowercase(Locale.US)
            ?.takeIf { it.matches(HEX_ENTROPY_REGEX) }
            ?: return missingOrMalformed(fields.values, "desktop_nonce", qr = true)
        val secret = required("qr_secret")
            ?.takeIf { it.matches(QR_SECRET_REGEX) }
            ?: return missingOrMalformed(fields.values, "qr_secret", qr = true)

        if (expiresAt <= nowEpochMillis) {
            return invalid(
                error = PairingPayloadError.EXPIRED,
                field = "expires_at_epoch_millis",
                message = "QR expired. Cannot reach desktop. Rescan the QR code or enter the endpoint and 6-digit code manually.",
                recoveryAction = PairingRecoveryAction.RESCAN_OR_MANUAL_EDIT,
            )
        }

        return PairingParseResult.Valid(
            PairingPayloadV1(
                v = version,
                sid = sid,
                host = host,
                port = port,
                expiresAtEpochMillis = expiresAt,
                desktopSpkiSha256 = fingerprint,
                desktopNonce = nonce,
                qrSecret = secret,
            ),
        )
    }

    fun parseManual(
        host: String,
        port: String,
        code: String,
        desktopSpkiSha256Suffix: String,
    ): PairingParseResult<ManualPairingPayload> {
        val normalizedHost = host.trim()
        val normalizedCode = code.trim()
        val normalizedSuffix = desktopSpkiSha256Suffix.trim().lowercase(Locale.US)
        val parsedPort = requiredInt("port", port.trim())

        if (normalizedHost.isBlank()) return missing("host", qr = false)
        if (parsedPort == null) return malformed("port", qr = false)
        if (!normalizedCode.matches(MANUAL_CODE_REGEX)) return malformed("code", qr = false)
        if (!normalizedSuffix.matches(FINGERPRINT_SUFFIX_REGEX)) return malformed("desktop_spki_sha256_suffix", qr = false)

        return PairingParseResult.Valid(
            ManualPairingPayload(
                host = normalizedHost,
                port = parsedPort,
                code = normalizedCode,
                desktopSpkiSha256Suffix = normalizedSuffix,
            ),
        )
    }

    private fun parseQuery(rawQuery: String): ParsedFields {
        val values = mutableMapOf<String, String>()
        if (rawQuery.isBlank()) {
            return ParsedFields(values)
        }
        rawQuery.split("&").forEach { part ->
            val keyValue = part.split("=", limit = 2)
            if (keyValue.size != 2) {
                return ParsedFields(
                    values = values,
                    invalid = invalid(
                        error = PairingPayloadError.MALFORMED_FIELD,
                        message = "Malformed pairing QR field. Rescan the QR code.",
                        recoveryAction = PairingRecoveryAction.RESCAN_QR,
                    ),
                )
            }
            val key = decode(keyValue[0]) ?: return ParsedFields(
                values = values,
                invalid = malformedQueryField(),
            )
            val value = decode(keyValue[1]) ?: return ParsedFields(
                values = values,
                invalid = malformedQueryField(),
            )
            if (values.containsKey(key)) {
                return ParsedFields(
                    values = values,
                    invalid = invalid(
                        error = PairingPayloadError.DUPLICATE_FIELD,
                        field = key,
                        message = "Duplicate pairing QR field. Rescan the QR code.",
                        recoveryAction = PairingRecoveryAction.RESCAN_QR,
                    ),
                )
            }
            values[key] = value
        }
        return ParsedFields(values)
    }

    private fun decode(value: String): String? =
        runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrNull()

    private fun malformedQueryField(): PairingParseResult.Invalid =
        invalid(
            error = PairingPayloadError.MALFORMED_FIELD,
            message = "Malformed pairing QR field. Rescan the QR code.",
            recoveryAction = PairingRecoveryAction.RESCAN_QR,
        )

    private fun requiredInt(field: String, value: String?): Int? =
        value?.toIntOrNull()?.takeIf { it in PORT_RANGE || field == "v" }

    private fun requiredLong(field: String, value: String?): Long? =
        value?.toLongOrNull()?.takeIf { it > 0L }

    private fun missingOrMalformed(values: Map<String, String>, field: String, qr: Boolean): PairingParseResult.Invalid =
        if (values[field] == null || values[field].isNullOrBlank()) missing(field, qr) else malformed(field, qr)

    private fun missing(field: String, qr: Boolean): PairingParseResult.Invalid =
        invalid(
            error = PairingPayloadError.MISSING_FIELD,
            field = field,
            message = if (qr) "Pairing QR is missing $field. Rescan the QR code." else "Manual pairing is missing $field.",
            recoveryAction = if (qr) PairingRecoveryAction.RESCAN_QR else PairingRecoveryAction.MANUAL_EDIT,
        )

    private fun malformed(field: String, qr: Boolean): PairingParseResult.Invalid =
        invalid(
            error = PairingPayloadError.MALFORMED_FIELD,
            field = field,
            message = if (qr) "Pairing QR has invalid $field. Rescan the QR code." else "Manual pairing has invalid $field.",
            recoveryAction = if (qr) PairingRecoveryAction.RESCAN_QR else PairingRecoveryAction.MANUAL_EDIT,
        )

    private fun invalidUri(): PairingParseResult.Invalid =
        invalid(
            error = PairingPayloadError.UNSUPPORTED_URI,
            message = "Unsupported pairing QR. Rescan the QR code.",
            recoveryAction = PairingRecoveryAction.RESCAN_QR,
        )

    private fun invalid(
        error: PairingPayloadError,
        message: String,
        field: String? = null,
        recoveryAction: PairingRecoveryAction,
    ): PairingParseResult.Invalid =
        PairingParseResult.Invalid(
            error = error,
            message = message,
            field = field,
            recoveryAction = recoveryAction,
        )

    private data class ParsedFields(
        val values: Map<String, String>,
        val invalid: PairingParseResult.Invalid? = null,
    )
}

private const val VERSION = 1
private const val MAX_QR_PAYLOAD_LENGTH = 4096
private val PORT_RANGE = 1..65_535
private val FINGERPRINT_REGEX = Regex("[0-9a-f]{64}")
private val FINGERPRINT_SUFFIX_REGEX = Regex("[0-9a-f]{8,64}")
private val HEX_ENTROPY_REGEX = Regex("[0-9a-f]{32,}")
private val QR_SECRET_REGEX = Regex("[A-Za-z0-9_-]{32,}")
private val MANUAL_CODE_REGEX = Regex("\\d{6}")
