package com.btgun.host.session

enum class DesktopLinkPhase(val wireName: String) {
    IDLE("idle"),
    SCANNING_QR("scanning_qr"),
    CONNECTING("connecting"),
    PAIRING_PROOF("pairing_proof"),
    CONNECTED("connected"),
    DEGRADED("degraded"),
    DISCONNECTED("disconnected"),
    TRUST_PROBLEM("trust_problem"),
}

data class DesktopLinkState(
    val phase: DesktopLinkPhase = DesktopLinkPhase.IDLE,
    val desktopDisplayName: String? = null,
    val fingerprintSuffix: String? = null,
    val heartbeatAgeMillis: Long? = null,
    val lastControlError: String? = null,
    val profileDisplayName: String? = null,
    val profileRevision: Long? = null,
    val primaryActionLabel: String = defaultPrimaryAction(phase, desktopDisplayName),
    val manualActionLabel: String = "Enter manually",
    val diagnosticText: String = defaultDiagnostic(
        phase,
        desktopDisplayName,
        fingerprintSuffix,
        heartbeatAgeMillis,
        lastControlError,
        profileDisplayName,
        profileRevision,
    ),
)

private fun defaultPrimaryAction(
    phase: DesktopLinkPhase,
    desktopDisplayName: String?,
): String =
    when {
        desktopDisplayName != null && phase in setOf(DesktopLinkPhase.IDLE, DesktopLinkPhase.DISCONNECTED, DesktopLinkPhase.CONNECTED) ->
            "Use trusted desktop"
        phase == DesktopLinkPhase.DISCONNECTED -> "Rescan QR"
        else -> "Scan desktop QR"
    }

private fun defaultDiagnostic(
    phase: DesktopLinkPhase,
    desktopDisplayName: String?,
    fingerprintSuffix: String?,
    heartbeatAgeMillis: Long?,
    lastControlError: String?,
    profileDisplayName: String?,
    profileRevision: Long?,
): String {
    val base = when (phase) {
        DesktopLinkPhase.IDLE -> if (desktopDisplayName == null) {
            "No desktop paired yet. Start pairing on the desktop, then scan the QR code from Android."
        } else {
            "Trusted desktop ready: $desktopDisplayName."
        }
        DesktopLinkPhase.SCANNING_QR -> "Scanning desktop QR. Keep the desktop pairing QR visible."
        DesktopLinkPhase.CONNECTING -> "Connecting to desktop endpoint from QR or manual entry."
        DesktopLinkPhase.PAIRING_PROOF -> "Proving desktop identity before trusted control messages."
        DesktopLinkPhase.CONNECTED -> "Connected to desktop pairing/control channel."
        DesktopLinkPhase.DEGRADED -> "Desktop control channel degraded."
        DesktopLinkPhase.DISCONNECTED -> "Cannot reach desktop. Rescan the QR code or enter the endpoint and 6-digit code manually."
        DesktopLinkPhase.TRUST_PROBLEM ->
            "Desktop identity changed. The saved fingerprint does not match this desktop. Pair again only if you trust the new desktop."
    }
    return listOfNotNull(
        base,
        desktopDisplayName?.let { "desktop=$it" },
        fingerprintSuffix?.let { "fingerprint_suffix=$it" },
        heartbeatAgeMillis?.let { "heartbeat=${it / 1_000L}s" },
        lastControlError?.let { "last_control_error=$it" },
        profileDisplayName?.let { profile -> "profile=$profile" + (profileRevision?.let { " rev=$it" } ?: "") },
    ).joinToString(" | ")
}
