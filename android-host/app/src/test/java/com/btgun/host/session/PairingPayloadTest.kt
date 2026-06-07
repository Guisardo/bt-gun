package com.btgun.host.session

fun main() {
    qrParserAcceptsDesktopPairingUri()
    qrParserRejectsMissingAndExpiredPayloadsWithTypedErrors()
    manualParserRequiresEndpointSixDigitCodeAndFingerprintSuffix()
}

private fun qrParserAcceptsDesktopPairingUri() {
    val result = PairingPayload.parseQrUri(validQrUri(), nowEpochMillis = 1_700_000_000_000L)

    val valid = expectValid<PairingPayloadV1>("valid qr", result)
    expectEquals("version", 1, valid.value.v)
    expectEquals("sid", "session-001", valid.value.sid)
    expectEquals("host", "192.168.1.44", valid.value.host)
    expectEquals("port", 44383, valid.value.port)
    expectEquals("expiry", 1_700_000_120_000L, valid.value.expiresAtEpochMillis)
    expectEquals("fingerprint", FINGERPRINT, valid.value.desktopSpkiSha256)
    expectEquals("nonce", NONCE, valid.value.desktopNonce)
    expectEquals("secret", QR_SECRET, valid.value.qrSecret)
}

private fun qrParserRejectsMissingAndExpiredPayloadsWithTypedErrors() {
    val missingSid = PairingPayload.parseQrUri(
        validQrUri().replace("sid=session-001&", ""),
        nowEpochMillis = 1_700_000_000_000L,
    )
    val missing = expectInvalid("missing sid", missingSid)
    expectEquals("missing error", PairingPayloadError.MISSING_FIELD, missing.error)
    expectEquals("missing field", "sid", missing.field)
    expectEquals("missing recovery", PairingRecoveryAction.RESCAN_QR, missing.recoveryAction)

    val expired = PairingPayload.parseQrUri(validQrUri(), nowEpochMillis = 1_700_000_130_000L)
    val invalid = expectInvalid("expired qr", expired)
    expectEquals("expired error", PairingPayloadError.EXPIRED, invalid.error)
    expectEquals("expired recovery", PairingRecoveryAction.RESCAN_OR_MANUAL_EDIT, invalid.recoveryAction)
    expectContains("expired message", invalid.message, "Rescan")
    listOf("LAN " + "discovery", "service " + "discovery").forEach { forbidden ->
        expectFalse("expired does not start $forbidden", invalid.message.contains(forbidden, ignoreCase = true))
    }
}

private fun manualParserRequiresEndpointSixDigitCodeAndFingerprintSuffix() {
    val result = PairingPayload.parseManual(
        host = "192.168.1.44",
        port = "44383",
        code = "123456",
        desktopSpkiSha256Suffix = "11223344",
        sid = "session-001",
    )

    val valid = expectValid<ManualPairingPayload>("valid manual", result)
    expectEquals("manual host", "192.168.1.44", valid.value.host)
    expectEquals("manual port", 44383, valid.value.port)
    expectEquals("manual code", "123456", valid.value.code)
    expectEquals("manual suffix", "11223344", valid.value.desktopSpkiSha256Suffix)
    expectEquals("manual sid", "session-001", valid.value.sid)

    val malformedCode = PairingPayload.parseManual(
        host = "192.168.1.44",
        port = "44383",
        code = "12ab56",
        desktopSpkiSha256Suffix = "11223344",
        sid = "session-001",
    )
    val invalidCode = expectInvalid("manual code", malformedCode)
    expectEquals("code error", PairingPayloadError.MALFORMED_FIELD, invalidCode.error)
    expectEquals("code field", "code", invalidCode.field)
    expectEquals("code recovery", PairingRecoveryAction.MANUAL_EDIT, invalidCode.recoveryAction)

    val missingHost = PairingPayload.parseManual(
        host = "",
        port = "44383",
        code = "123456",
        desktopSpkiSha256Suffix = "11223344",
        sid = "session-001",
    )
    val invalidHost = expectInvalid("manual host", missingHost)
    expectEquals("host error", PairingPayloadError.MISSING_FIELD, invalidHost.error)
    expectEquals("host field", "host", invalidHost.field)
}

private fun validQrUri(): String =
    "btgun://pair?" +
        "v=1&" +
        "sid=session-001&" +
        "host=192.168.1.44&" +
        "port=44383&" +
        "expires_at_epoch_millis=1700000120000&" +
        "desktop_spki_sha256=$FINGERPRINT&" +
        "desktop_nonce=$NONCE&" +
        "qr_secret=$QR_SECRET"

private inline fun <reified T> expectValid(label: String, result: PairingParseResult<T>): PairingParseResult.Valid<T> =
    when (result) {
        is PairingParseResult.Valid -> result
        is PairingParseResult.Invalid -> throw AssertionError("$label expected valid but was ${result.error}: ${result.message}")
    }

private fun expectInvalid(label: String, result: PairingParseResult<*>): PairingParseResult.Invalid =
    when (result) {
        is PairingParseResult.Valid -> throw AssertionError("$label expected invalid but was ${result.value}")
        is PairingParseResult.Invalid -> result
    }

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectFalse(label: String, condition: Boolean) {
    if (condition) {
        throw AssertionError(label)
    }
}

private fun expectContains(label: String, actual: String, expectedPart: String) {
    if (!actual.contains(expectedPart)) {
        throw AssertionError("$label expected <$actual> to contain <$expectedPart>")
    }
}

private const val FINGERPRINT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
private const val NONCE = "0123456789abcdef0123456789abcdef"
private const val QR_SECRET = "abcdefghijklmnopqrstuvwxyzABCDEF"
