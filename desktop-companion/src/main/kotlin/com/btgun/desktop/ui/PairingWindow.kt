package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.macos.MacosBackendRuntime
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.windows.WindowsBackendRuntime
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeDiagnostics
import com.btgun.desktop.haptics.HapticCommand
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.pairing.LocalEndpoint
import com.btgun.desktop.pairing.ManualPairingPayload
import com.btgun.desktop.pairing.PairingSecurityState
import com.btgun.desktop.pairing.PairingSession
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.pairing.QrCodeRenderer
import com.btgun.desktop.security.SecretRedactor
import com.btgun.desktop.transport.InputStreamLifecycleState
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

class PairingWindow(
    private val registry: PairingSessionRegistry = PairingSessionRegistry(),
    private val controlServer: ControlServer = ControlServer(registry),
    private val windowsBackendRuntime: WindowsBackendRuntime? = null,
    private val windowsBackendStartupDiagnostic: String = "disabled",
    private val macosBackendRuntime: MacosBackendRuntime? = null,
    private val macosBackendStartupDiagnostic: String = "disabled",
) {
    private val frame = JFrame("BT Gun Desktop")
    private val title = JLabel("BT Gun Desktop")
    private val state = JLabel(stateText(DesktopSessionUiState.IDLE))
    private val endpoint = JLabel("Endpoint: not selected")
    private val qr = JLabel("Start pairing", SwingConstants.CENTER)
    private val countdown = JLabel("Countdown: inactive")
    private val manual = JLabel("Manual fallback: inactive")
    private val diagnostics = JLabel(diagnosticsHtml(DesktopSessionUiState.IDLE, lastControlError = null))
    private val action = JButton("Start pairing")
    private val hapticAction = JButton("Test haptic")
    private var session: PairingSession? = null
    private var displayState = DesktopSessionUiState.IDLE
    private var serverAuthenticated = false
    private var packetStreamState = InputStreamLifecycleState.STOPPED
    private var lastControlError: String? = null
    private var lastHapticStatus: String = "inactive"
    private var activeProfileMetadata: ProfileMetadata? = null
    private var lastProfileUpdateElapsedNanos: Long? = null
    private var lastMappedProductStream: Boolean = false
    private var lastRawDebugEnabled: Boolean = false
    private var windowsBackendDiagnostics: WindowsBackendRuntimeDiagnostics? = windowsBackendRuntime?.diagnostics()
    private var macosBackendDiagnostics: MacosBackendRuntimeDiagnostics? = macosBackendRuntime?.diagnostics()

    init {
        title.font = title.font.deriveFont(Font.BOLD, 22f)
        qr.preferredSize = Dimension(QR_SIZE, QR_SIZE)
        qr.minimumSize = Dimension(QR_SIZE, QR_SIZE)
        manual.verticalAlignment = SwingConstants.TOP
        manual.border = BorderFactory.createTitledBorder("Manual fallback")
        diagnostics.verticalAlignment = SwingConstants.TOP
        diagnostics.border = BorderFactory.createTitledBorder("Control state")

        controlServer.onSessionStateChanged = { serverState ->
            SwingUtilities.invokeLater {
                applyServerState(serverState)
            }
        }
        controlServer.onUdpInputStateChanged = { streamState ->
            SwingUtilities.invokeLater {
                packetStreamState = streamState
                updateDiagnostics()
            }
        }
        controlServer.onProfileMetadataReceived = { metadata ->
            SwingUtilities.invokeLater {
                activeProfileMetadata = metadata
                lastProfileUpdateElapsedNanos = System.nanoTime()
                lastRawDebugEnabled = metadata.rawDebugEnabled
                updateDiagnostics()
            }
        }
        controlServer.onUdpInputReceived = { input ->
            SwingUtilities.invokeLater {
                lastMappedProductStream = input.mappedProductStream
                lastRawDebugEnabled = input.rawDebugEnabled
                updateDiagnostics()
            }
        }
        controlServer.onUdpInputRejected = { reason ->
            SwingUtilities.invokeLater {
                lastControlError = "UDP input rejected: ${reason.name.lowercase()}"
                updateDiagnostics()
            }
        }
        controlServer.onHapticResultReceived = { result ->
            SwingUtilities.invokeLater {
                lastHapticStatus = hapticStatusText(result)
                updateDiagnostics()
            }
        }
        windowsBackendRuntime?.onDiagnosticsChanged = { backendDiagnostics ->
            SwingUtilities.invokeLater {
                windowsBackendDiagnostics = backendDiagnostics
                updateDiagnostics()
            }
        }
        windowsBackendRuntime?.attach(controlServer)
        macosBackendRuntime?.onDiagnosticsChanged = { backendDiagnostics ->
            SwingUtilities.invokeLater {
                macosBackendDiagnostics = backendDiagnostics
                updateDiagnostics()
            }
        }
        macosBackendRuntime?.attach(controlServer)

        action.addActionListener {
            startPairing()
        }
        hapticAction.isEnabled = false
        hapticAction.addActionListener {
            sendTestHaptic()
        }

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane.add(content(), BorderLayout.CENTER)
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) {
                    macosBackendRuntime?.close()
                    windowsBackendRuntime?.close()
                    controlServer.stop()
                }
            },
        )
        frame.pack()
        frame.setLocationRelativeTo(null)

        Timer(1_000) {
            refreshSession()
        }.start()
    }

    fun show() {
        frame.isVisible = true
    }

    private fun content(): JPanel {
        val root = JPanel(BorderLayout(24, 24))
        root.border = BorderFactory.createEmptyBorder(32, 32, 32, 32)

        val header = JPanel(GridLayout(3, 1, 0, 8))
        header.add(title)
        header.add(state)
        header.add(endpoint)

        val primary = JPanel()
        primary.layout = BoxLayout(primary, BoxLayout.Y_AXIS)
        primary.add(qr)
        primary.add(Box.createVerticalStrut(8))
        primary.add(countdown)

        val side = JPanel()
        side.layout = BoxLayout(side, BoxLayout.Y_AXIS)
        side.add(manual)
        side.add(Box.createVerticalStrut(16))
        side.add(diagnostics)
        side.add(Box.createVerticalStrut(16))
        side.add(action)
        side.add(Box.createVerticalStrut(8))
        side.add(hapticAction)

        root.add(header, BorderLayout.NORTH)
        root.add(primary, BorderLayout.CENTER)
        root.add(side, BorderLayout.EAST)
        return root
    }

    private fun startPairing() {
        session = registry.startPairing()
        val current = session ?: return
        renderSession(current)
    }

    private fun renderSession(current: PairingSession) {
        displayState = DesktopSessionUiState.PAIRING_READY
        serverAuthenticated = false
        lastControlError = null
        lastHapticStatus = "inactive"
        startControlServer(current)
        state.text = stateText(displayState)
        endpoint.text = endpointText(current.endpoint)
        qr.icon = ImageIcon(QrCodeRenderer.render(current.qrPayload.toPairingUri(), size = QR_SIZE))
        qr.text = null
        action.text = "Restart pairing"
        manual.text = manualFallbackHtml(current.manualPayload)
        refreshSession()
        frame.pack()
    }

    private fun startControlServer(current: PairingSession) {
        runCatching {
            controlServer.start(port = current.endpoint.port, host = current.endpoint.host)
        }.onFailure { error ->
            displayState = DesktopSessionUiState.DISCONNECTED
            lastControlError = SecretRedactor.redact("Control server start failed: ${errorSummary(error)}")
        }
    }

    private fun refreshSession() {
        val current = session
        if (current == null) {
            countdown.text = "Countdown: inactive"
            state.text = stateText(DesktopSessionUiState.IDLE)
            hapticAction.isEnabled = false
            updateDiagnostics(DesktopSessionUiState.IDLE)
            return
        }

        val now = System.currentTimeMillis()
        displayState = stateFromSecurity(current, now)
        countdown.text = countdownText(current.expiresAtEpochMillis, now)
        state.text = stateText(displayState)
        hapticAction.isEnabled = hapticButtonEnabled(displayState, serverAuthenticated)
        updateDiagnostics()
        if (displayState == DesktopSessionUiState.EXPIRED) {
            registry.expire(now)
            startPairing()
        }
    }

    private fun applyServerState(serverState: ControlServerSessionState) {
        displayState = when (serverState) {
            ControlServerSessionState.STARTED -> DesktopSessionUiState.PAIRING_READY
            ControlServerSessionState.STOPPED -> displayState
            ControlServerSessionState.ANDROID_CONNECTED -> DesktopSessionUiState.ANDROID_CONNECTED
            ControlServerSessionState.AUTHENTICATED -> DesktopSessionUiState.AUTHENTICATED
            ControlServerSessionState.DEGRADED -> DesktopSessionUiState.DEGRADED
            ControlServerSessionState.DISCONNECTED -> DesktopSessionUiState.DISCONNECTED
            ControlServerSessionState.RATE_LIMITED -> DesktopSessionUiState.RATE_LIMITED
        }
        serverAuthenticated = when (serverState) {
            ControlServerSessionState.AUTHENTICATED -> true
            ControlServerSessionState.DEGRADED -> serverAuthenticated
            else -> false
        }
        state.text = stateText(displayState)
        hapticAction.isEnabled = hapticButtonEnabled(displayState, serverAuthenticated)
        updateDiagnostics()
    }

    private fun sendTestHaptic() {
        val command = smokeHapticCommand("ui-test-${System.nanoTime()}")
        lastHapticStatus = when (val result = controlServer.sendHapticCommand(command)) {
            HapticSendResult.Sent -> "queued: ${command.commandId}"
            HapticSendResult.NoActiveSession -> "not connected"
            is HapticSendResult.Rejected -> "rejected: ${result.error.name.lowercase()}"
            is HapticSendResult.Failed -> "failed: ${result.reason}"
        }
        updateDiagnostics()
    }

    private fun updateDiagnostics(state: DesktopSessionUiState = displayState) {
        diagnostics.text = diagnosticsHtml(
            state = state,
            packetState = packetStreamState,
            lastControlError = lastControlError,
            lastHapticStatus = lastHapticStatus,
            profile = activeProfileMetadata,
            mappedProductStream = lastMappedProductStream,
            rawDebugEnabled = lastRawDebugEnabled,
            lastProfileUpdateElapsedNanos = lastProfileUpdateElapsedNanos,
            nowElapsedNanos = System.nanoTime(),
            windowsBackendStatus = windowsBackendStatusText(
                diagnostics = windowsBackendDiagnostics,
                startupDiagnostic = windowsBackendStartupDiagnostic,
            ),
            macosBackendStatus = macosBackendStatusText(
                diagnostics = macosBackendDiagnostics,
                startupDiagnostic = macosBackendStartupDiagnostic,
            ),
        )
    }

    private fun stateFromSecurity(current: PairingSession, nowEpochMillis: Long): DesktopSessionUiState =
        when (registry.securityState(current.sid, nowEpochMillis)) {
            PairingSecurityState.PENDING -> when (displayState) {
                DesktopSessionUiState.ANDROID_CONNECTED,
                DesktopSessionUiState.DEGRADED,
                DesktopSessionUiState.DISCONNECTED,
                -> displayState
                else -> DesktopSessionUiState.PAIRING_READY
            }
            PairingSecurityState.RATE_LIMITED -> DesktopSessionUiState.RATE_LIMITED
            PairingSecurityState.ACCEPTED -> displayState
            PairingSecurityState.EXPIRED -> DesktopSessionUiState.EXPIRED
            PairingSecurityState.MISSING -> when (displayState) {
                DesktopSessionUiState.AUTHENTICATED,
                DesktopSessionUiState.DEGRADED,
                -> displayState
                else -> DesktopSessionUiState.DISCONNECTED
            }
        }

    companion object {
        internal const val QR_SIZE = 420

        internal fun requiredStateLabels(): List<String> =
            DesktopSessionUiState.entries.map { it.label }

        internal fun requiredTransportStateLabels(): List<String> =
            InputStreamLifecycleState.entries.map { it.label }

        internal fun transportDiagnosticsHtml(state: InputStreamLifecycleState): String =
            """
                <html>
                <body>
                <p>Packet stream: ${state.label}</p>
                </body>
                </html>
            """.trimIndent()

        internal fun profileDiagnosticsHtml(
            profile: ProfileMetadata?,
            packetState: InputStreamLifecycleState,
            mappedProductStream: Boolean,
            rawDebugEnabled: Boolean,
            lastProfileUpdateElapsedNanos: Long?,
            nowElapsedNanos: Long,
        ): String =
            """
                <p><b>Active Android profile:</b> ${activeProfileText(profile)}</p>
                <p><b>Profile source:</b> ${escapeHtml(profile?.source ?: "unknown")}</p>
                <p><b>Mapped stream:</b> ${packetState.label} | mapped=$mappedProductStream | raw_debug=${if (rawDebugEnabled) "on" else "off"}</p>
                <p><b>Last profile update:</b> ${profileUpdateText(lastProfileUpdateElapsedNanos, nowElapsedNanos)}</p>
            """.trimIndent()

        internal fun smokeHapticCommand(commandId: String): HapticCommand =
            HapticCommand(
                commandId = commandId,
                strength = 0.6,
                durationMs = 80L,
                ttlMs = 500L,
            )

        internal fun hapticButtonEnabled(state: DesktopSessionUiState): Boolean =
            hapticButtonEnabled(state, serverAuthenticated = state == DesktopSessionUiState.AUTHENTICATED)

        internal fun hapticButtonEnabled(state: DesktopSessionUiState, serverAuthenticated: Boolean): Boolean =
            state == DesktopSessionUiState.AUTHENTICATED && serverAuthenticated

        internal fun hapticStatusText(result: HapticResult): String =
            "${result.status.wireName}: ${result.detail}"

        internal fun windowsBackendStatusText(
            diagnostics: WindowsBackendRuntimeDiagnostics?,
            startupDiagnostic: String,
        ): String {
            if (diagnostics == null) {
                return startupDiagnostic
            }
            return "lifecycle=${diagnostics.lifecycleState.label()}, " +
                "lastPublish=${diagnostics.lastPublishResult.label()}, " +
                "stale=${diagnostics.stale}, " +
                "lastHapticSend=${diagnostics.lastHapticSendResult.label()}, " +
                "routed=${diagnostics.outputHapticCommandsRouted}"
        }

        internal fun macosBackendStatusText(
            diagnostics: MacosBackendRuntimeDiagnostics?,
            startupDiagnostic: String,
        ): String {
            if (diagnostics == null) {
                return startupDiagnostic
            }
            return "lifecycle=${diagnostics.lifecycleState.label()}, " +
                "lastPublish=${diagnostics.lastPublishResult.label()}, " +
                "stale=${diagnostics.stale}, " +
                "lastHapticSend=${diagnostics.lastHapticSendResult.label()}, " +
                "routed=${diagnostics.outputHapticCommandsRouted}, " +
                "helper=${diagnostics.helperStatus.helperLabel()}"
        }

        internal fun endpointText(endpoint: LocalEndpoint): String =
            "Endpoint: ${endpoint.host}:${endpoint.port}"

        internal fun countdownText(expiresAtEpochMillis: Long, nowEpochMillis: Long): String {
            val remaining = ((expiresAtEpochMillis - nowEpochMillis).coerceAtLeast(0L) + 999L) / 1_000L
            return "Expires in: ${remaining}s"
        }

        internal fun manualFallbackHtml(payload: ManualPairingPayload): String =
            """
                <html>
                <body>
                <p>Use this endpoint and 6-digit code only if QR scan is unavailable.</p>
                <p><b>Endpoint:</b> ${escapeHtml(payload.host)}:${payload.port}</p>
                <p><b>Port:</b> ${payload.port}</p>
                <p><b>6-digit code:</b> ${payload.code}</p>
                <p><b>Fingerprint suffix:</b> ${escapeHtml(payload.desktopSpkiSha256Suffix)}</p>
                </body>
                </html>
            """.trimIndent()

        private fun stateText(state: DesktopSessionUiState): String =
            "State: ${state.label}"

        private fun diagnosticsHtml(
            state: DesktopSessionUiState,
            packetState: InputStreamLifecycleState = InputStreamLifecycleState.STOPPED,
            lastControlError: String?,
            lastHapticStatus: String = "inactive",
            profile: ProfileMetadata? = null,
            mappedProductStream: Boolean = false,
            rawDebugEnabled: Boolean = false,
            lastProfileUpdateElapsedNanos: Long? = null,
            nowElapsedNanos: Long = System.nanoTime(),
            windowsBackendStatus: String = "disabled",
            macosBackendStatus: String = "disabled",
        ): String {
            val safeError = SecretRedactor.redact(lastControlError ?: "none")
            val profileRows = profileDiagnosticsHtml(
                profile = profile,
                packetState = packetState,
                mappedProductStream = mappedProductStream,
                rawDebugEnabled = rawDebugEnabled,
                lastProfileUpdateElapsedNanos = lastProfileUpdateElapsedNanos,
                nowElapsedNanos = nowElapsedNanos,
            )
            return """
                <html>
                <body>
                <p><b>Session:</b> ${state.label}</p>
                <p>Packet stream: ${packetState.label}</p>
                $profileRows
                <p><b>Windows backend:</b> ${escapeHtml(windowsBackendStatus)}</p>
                <p><b>macOS backend:</b> ${escapeHtml(macosBackendStatus)}</p>
                <p><b>Last control error:</b> ${escapeHtml(safeError)}</p>
                <p><b>Phone haptic:</b> ${escapeHtml(lastHapticStatus)}</p>
                </body>
                </html>
            """.trimIndent()
        }

        private fun activeProfileText(profile: ProfileMetadata?): String =
            if (profile == null) {
                "unknown"
            } else {
                "${escapeHtml(profile.displayName)} | id=${escapeHtml(profile.profileId)} | rev=${profile.revision}"
            }

        private fun profileUpdateText(lastProfileUpdateElapsedNanos: Long?, nowElapsedNanos: Long): String =
            lastProfileUpdateElapsedNanos?.let { last ->
                val elapsedSeconds = ((nowElapsedNanos - last).coerceAtLeast(0L) / 1_000_000_000L)
                "${elapsedSeconds}s ago"
            } ?: "none"

        private fun BackendLifecycleState.label(): String =
            name.lowercase()

        private fun BackendPublishResult?.label(): String =
            when (this) {
                null -> "none"
                BackendPublishResult.Published -> "published"
                is BackendPublishResult.Rejected -> "rejected"
            }

        private fun HapticSendResult?.label(): String =
            when (this) {
                null -> "none"
                HapticSendResult.Sent -> "sent"
                HapticSendResult.NoActiveSession -> "no-session"
                is HapticSendResult.Rejected -> "rejected"
                is HapticSendResult.Failed -> "failed"
            }

        private fun com.btgun.desktop.backend.macos.MacosHidHelperStatus.helperLabel(): String =
            "active=$deviceActive, visible=$osVisible, setReport=$setReportCallbackSeen, " +
                "submitted=$inputReportsSubmitted, queued=$outputReportsQueued, " +
                "malformedIn=$malformedInputReports, malformedOut=$malformedOutputReports"

        private fun escapeHtml(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")

        private fun errorSummary(error: Throwable): String =
            listOfNotNull(
                error.javaClass.simpleName.ifBlank { "error" } + error.message?.let { ": $it" }.orEmpty(),
                error.cause?.let { cause ->
                    "cause=${cause.javaClass.simpleName.ifBlank { "error" }}${cause.message?.let { ": $it" }.orEmpty()}"
                },
            ).joinToString(" ")
    }
}

internal enum class DesktopSessionUiState(val label: String) {
    IDLE("idle"),
    PAIRING_READY("pairing ready"),
    ANDROID_CONNECTED("android connected"),
    AUTHENTICATED("authenticated"),
    DEGRADED("degraded"),
    DISCONNECTED("disconnected"),
    EXPIRED("expired"),
    RATE_LIMITED("rate limited"),
}
