package com.btgun.desktop.ui

import com.btgun.desktop.pairing.PairingSession
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.pairing.QrCodeRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer

class PairingWindow(
    private val registry: PairingSessionRegistry = PairingSessionRegistry(),
) {
    private val frame = JFrame("BT Gun Desktop")
    private val title = JLabel("BT Gun Desktop")
    private val state = JLabel("idle")
    private val endpoint = JLabel("Endpoint: not selected")
    private val qr = JLabel("Start pairing", SwingConstants.CENTER)
    private val countdown = JLabel("Countdown: inactive")
    private val manual = JLabel("Manual fallback: inactive")
    private val action = JButton("Start pairing")
    private var session: PairingSession? = null

    init {
        title.font = title.font.deriveFont(Font.BOLD, 22f)
        qr.preferredSize = Dimension(260, 260)
        manual.verticalAlignment = SwingConstants.TOP
        manual.border = BorderFactory.createTitledBorder("Manual fallback")

        action.addActionListener {
            startPairing()
        }

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane.add(content(), BorderLayout.CENTER)
        frame.pack()
        frame.setLocationRelativeTo(null)

        Timer(1_000) {
            refreshCountdown()
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
        side.add(action)

        root.add(header, BorderLayout.NORTH)
        root.add(primary, BorderLayout.CENTER)
        root.add(side, BorderLayout.EAST)
        return root
    }

    private fun startPairing() {
        session = registry.startPairing()
        val current = session ?: return
        state.text = "pairing ready"
        endpoint.text = "Endpoint: ${current.endpoint.host}:${current.endpoint.port}"
        qr.icon = ImageIcon(QrCodeRenderer.render(current.qrPayload.toPairingUri(), size = 260))
        qr.text = null
        action.text = "Restart pairing"
        manual.text = """
            <html>
            <body>
            <p>Use this endpoint and 6-digit code only if QR scan is unavailable.</p>
            <p><b>Host:</b> ${current.manualPayload.host}</p>
            <p><b>Port:</b> ${current.manualPayload.port}</p>
            <p><b>Code:</b> ${current.manualPayload.code}</p>
            <p><b>Fingerprint suffix:</b> ${current.manualPayload.desktopSpkiSha256Suffix}</p>
            </body>
            </html>
        """.trimIndent()
        refreshCountdown()
        frame.pack()
    }

    private fun refreshCountdown() {
        val current = session
        if (current == null) {
            countdown.text = "Countdown: inactive"
            return
        }

        val remaining = ((current.expiresAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1_000L
        countdown.text = "Expires in: ${remaining}s"
        if (remaining == 0L) {
            state.text = "expired"
            registry.expire()
        }
    }
}
