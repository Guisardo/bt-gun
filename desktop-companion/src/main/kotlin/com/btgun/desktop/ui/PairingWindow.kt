package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
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
) {
    private val frame = JFrame("BT Gun Desktop")
    private val title = JLabel("BT Gun Desktop")
    private val state = JLabel(stateText(DesktopSessionUiState.IDLE))
    private val endpoint = JLabel("Endpoint: not selected")
    private val qr = JLabel("Start pairing", SwingConstants.CENTER)
    private val countdown = JLabel("Countdown: inactive")
    private val manual = JLabel("Manual fallback: inactive")
    private val diagnostics = JLabel(diagnosticsHtml(DesktopSessionUiState.IDLE, null))
    private val action = JButton("Start pairing")
    private var session: PairingSession? = null
    private var displayState = DesktopSessionUiState.IDLE
    private var lastControlError: String? = null

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

        action.addActionListener {
            startPairing()
        }

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane.add(content(), BorderLayout.CENTER)
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) {
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
        lastControlError = null
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
            diagnostics.text = diagnosticsHtml(DesktopSessionUiState.IDLE, lastControlError)
            return
        }

        val now = System.currentTimeMillis()
        displayState = stateFromSecurity(current, now)
        countdown.text = countdownText(current.expiresAtEpochMillis, now)
        state.text = stateText(displayState)
        diagnostics.text = diagnosticsHtml(displayState, lastControlError)
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
        state.text = stateText(displayState)
        diagnostics.text = diagnosticsHtml(displayState, lastControlError)
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
            PairingSecurityState.ACCEPTED -> when (displayState) {
                DesktopSessionUiState.DEGRADED,
                DesktopSessionUiState.DISCONNECTED,
                -> displayState
                else -> DesktopSessionUiState.AUTHENTICATED
            }
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

        private fun diagnosticsHtml(state: DesktopSessionUiState, lastControlError: String?): String {
            val safeError = SecretRedactor.redact(lastControlError ?: "none")
            return """
                <html>
                <body>
                <p><b>Session:</b> ${state.label}</p>
                <p>Packet stream: ${InputStreamLifecycleState.STOPPED.label}</p>
                <p><b>Last control error:</b> ${escapeHtml(safeError)}</p>
                <p>Phone haptics use trusted control.</p>
                </body>
                </html>
            """.trimIndent()
        }

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
