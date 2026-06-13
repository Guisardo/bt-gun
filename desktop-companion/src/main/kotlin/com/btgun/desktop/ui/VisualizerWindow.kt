package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.VisualizerStatus
import com.btgun.desktop.haptics.HapticCommand
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

interface VisualizerWindowHandle {
    fun open()
    fun applyModel(model: VisualizerModel)
}

class VisualizerWindow(
    private val controlServer: ControlServer? = null,
    private val frame: JFrame = JFrame(windowTitle()),
) : VisualizerWindowHandle {
    private val title = JLabel(windowTitle())
    private val summary = JLabel(topSummaryPending())
    private val session = JLabel(emptyStateHeading())
    private val profile = JLabel("Profile: unknown")
    private val checklist = JLabel(checklistHtml(VisualizerModel.defaultChecklistRows()))
    private val gamepad = VisualizerPanels.liveGamepadPanel()
    private val metrics = JLabel(VisualizerMetricSnapshot.empty().headlineLatencyLabel)
    private val recenter = JLabel(labelsHtml(recenterStatusLabels(VisualizerModel.initial())))
    private val hapticAction = JButton(hapticButtonLabel())
    private val events = JLabel("Recent product events: none")
    private val rawDebug = JLabel("Raw debug off")
    private var currentModel = VisualizerModel.initial()

    init {
        title.font = title.font.deriveFont(Font.BOLD, DISPLAY_FONT_SIZE)
        summary.font = summary.font.deriveFont(Font.BOLD, DISPLAY_FONT_SIZE)
        session.font = session.font.deriveFont(Font.PLAIN, BODY_FONT_SIZE)
        profile.font = profile.font.deriveFont(Font.PLAIN, BODY_FONT_SIZE)

        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        hapticAction.isEnabled = false
        hapticAction.addActionListener {
            runPhoneHapticTest()
        }
        frame.contentPane.add(content(), BorderLayout.CENTER)
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) {
                    closeVisualizer()
                }
            },
        )
        frame.pack()
        frame.setLocationRelativeTo(null)
    }

    override fun open() {
        SwingUtilities.invokeLater {
            frame.isVisible = true
            frame.toFront()
        }
    }

    fun show() {
        open()
    }

    fun closeVisualizer() {
        frame.isVisible = false
        frame.dispose()
    }

    override fun applyModel(model: VisualizerModel) {
        SwingUtilities.invokeLater {
            currentModel = model
            summary.text = topSummaryPending()
            session.text = summaryFor(model.packetLifecycle.toDisplayState())
            profile.text = "Profile: ${model.profileSummary.displayName}"
            checklist.text = checklistHtml(model.checklistRows)
            gamepad.updateModel(model)
            metrics.text = labelsHtml(VisualizerPanels.metricsLabels(model.metrics))
            recenter.text = labelsHtml(recenterStatusLabels(model))
            hapticAction.isEnabled = controlServer != null && model.packetLifecycle == com.btgun.desktop.transport.InputStreamLifecycleState.ACTIVE
            events.text = labelsHtml(
                VisualizerPanels.eventStripLabels(
                    events = model.productEvents,
                    nowElapsedNanos = System.nanoTime(),
                ).ifEmpty { listOf("Recent product events: none") },
            )
            rawDebug.text = labelsHtml(VisualizerPanels.rawDebugLabels(model.rawDebug))
            frame.pack()
        }
    }

    private fun runPhoneHapticTest() {
        val now = System.nanoTime()
        val command = visualizerHapticCommand(nowElapsedNanos = now)
        val result = controlServer?.sendHapticCommand(command, nowElapsedNanos = now)
            ?: HapticSendResult.NoActiveSession
        currentModel = currentModel.withHapticSendResult(
            result = result,
            commandId = command.commandId,
            observedElapsedNanos = now,
        )
        applyModel(currentModel)
    }

    private fun content(): JPanel {
        val root = JPanel(BorderLayout(SPACING_LG, SPACING_LG))
        root.background = COLOR_BACKGROUND
        root.border = BorderFactory.createEmptyBorder(SPACING_XL, SPACING_XL, SPACING_XL, SPACING_XL)

        val header = JPanel(GridLayout(4, 1, 0, SPACING_SM))
        header.background = COLOR_BACKGROUND
        header.add(title)
        header.add(summary)
        header.add(session)
        header.add(profile)

        val center = JPanel(GridLayout(1, 2, SPACING_LG, 0))
        center.background = COLOR_BACKGROUND
        center.add(panel(requiredSectionLabels()[0], checklist))
        center.add(panel(requiredSectionLabels()[1], gamepad, Dimension(520, 320)))

        val south = JPanel()
        south.background = COLOR_BACKGROUND
        south.layout = BoxLayout(south, BoxLayout.Y_AXIS)
        south.add(panel(requiredSectionLabels()[2], metrics))
        south.add(Box.createVerticalStrut(SPACING_SM))
        south.add(panel("Recenter status", recenter))
        south.add(Box.createVerticalStrut(SPACING_SM))
        south.add(hapticAction)
        south.add(Box.createVerticalStrut(SPACING_SM))
        south.add(panel(requiredSectionLabels()[3], events))
        south.add(Box.createVerticalStrut(SPACING_SM))
        south.add(panel("Raw debug off", rawDebug))

        root.add(header, BorderLayout.NORTH)
        root.add(center, BorderLayout.CENTER)
        root.add(south, BorderLayout.SOUTH)
        return root
    }

    private fun panel(title: String, child: JComponent, preferredSize: Dimension? = null): JPanel {
        val panel = JPanel(BorderLayout(SPACING_SM, SPACING_SM))
        panel.background = COLOR_SURFACE
        panel.border = BorderFactory.createTitledBorder(title)
        if (child is JLabel) {
            child.font = child.font.deriveFont(Font.PLAIN, BODY_FONT_SIZE)
            child.verticalAlignment = SwingConstants.TOP
        }
        preferredSize?.let {
            child.preferredSize = it
            child.minimumSize = it
        }
        panel.add(child, BorderLayout.CENTER)
        return panel
    }

    companion object {
        private const val SPACING_SM = 8
        private const val SPACING_LG = 24
        private const val SPACING_XL = 32
        private const val BODY_FONT_SIZE = 14f
        private const val DISPLAY_FONT_SIZE = 22f
        private val COLOR_BACKGROUND = Color(0xF4, 0xF7, 0xF8)
        private val COLOR_SURFACE = Color.WHITE

        fun windowTitle(): String = "BT Gun Visualizer"

        fun emptyStateHeading(): String = "Waiting for authenticated session"

        fun emptyStateBody(): String = "Pair Android with the desktop companion to start live visualizer checks."

        fun topSummaryPending(): String = "Phase 9 checks pending"

        fun hapticButtonLabel(): String = "Run phone haptic test"

        fun hapticButtonEnabled(state: ControlServerSessionState): Boolean =
            state == ControlServerSessionState.AUTHENTICATED

        fun visualizerHapticCommand(nowElapsedNanos: Long): HapticCommand =
            HapticCommand(
                commandId = "visualizer-haptic-$nowElapsedNanos",
                strength = 0.6,
                durationMs = 80L,
                ttlMs = 500L,
            )

        fun hapticSendStatusText(result: HapticSendResult, commandId: String?): String =
            when (result) {
                HapticSendResult.Sent -> "Phone haptic queued"
                HapticSendResult.NoActiveSession -> "No active Android session. Pair Android before running haptic proof."
                is HapticSendResult.Rejected -> "Phone haptic failed. Check Android session and try again."
                is HapticSendResult.Failed -> "Phone haptic failed. Check Android session and try again."
            }

        fun hapticResultStatusText(status: HapticResultStatus): String =
            when (status) {
                HapticResultStatus.STARTED -> "Phone haptic confirmed"
                HapticResultStatus.EXPIRED,
                HapticResultStatus.UNSUPPORTED,
                HapticResultStatus.PERMISSION_BLOCKED,
                HapticResultStatus.FAILED,
                HapticResultStatus.CANCELLED,
                -> "Phone haptic failed. Check Android session and try again."
            }

        fun recenterStatusLabels(model: VisualizerModel): List<String> =
            listOf(
                model.recenter.aimZeroLabel,
                model.recenter.recenterInstruction,
                model.recenter.lastRecenterLabel,
            )

        fun requiredSectionLabels(): List<String> =
            listOf(
                "Acceptance checklist",
                "Live gamepad",
                "Latency and packet loss",
                "Recent product events",
            )

        fun summaryFor(state: VisualizerDisplayState): String =
            when (state) {
                VisualizerDisplayState.WAITING -> emptyStateHeading()
                VisualizerDisplayState.AUTHENTICATED -> "Android authenticated. Live visualizer checks can run."
                VisualizerDisplayState.DEGRADED -> "Visualizer is stale. Keep this window open to preserve the checklist."
                VisualizerDisplayState.DISCONNECTED ->
                    "Visualizer is disconnected. Reconnect Android or reopen pairing, then keep this window open to preserve the checklist."
            }

        fun closeBehavior(): VisualizerCloseBehavior =
            VisualizerCloseBehavior(
                disposeVisualizerUi = true,
                stopControlServer = false,
                stopBackendRuntimes = false,
            )

        private fun checklistHtml(rows: List<VisualizerChecklistRow>): String =
            rows.joinToString(
                separator = "",
                prefix = "<html><body>",
                postfix = "</body></html>",
            ) { row ->
                "<p><b>${escapeHtml(row.label)}:</b> ${row.state.name.lowercase()}</p>"
            }

        private fun labelsHtml(labels: List<String>): String =
            labels.joinToString(
                separator = "",
                prefix = "<html><body>",
                postfix = "</body></html>",
            ) { label ->
                "<p>${escapeHtml(label)}</p>"
            }

        private fun escapeHtml(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }
}

class VisualizerWindowFactory(
    private val createWindow: () -> VisualizerWindowHandle = { VisualizerWindow() },
) {
    private var window: VisualizerWindowHandle? = null

    fun open(): VisualizerWindowHandle {
        val current = window ?: createWindow().also { created ->
            window = created
        }
        current.open()
        return current
    }

    fun applyModel(model: VisualizerModel) {
        window?.applyModel(model)
    }
}

class VisualizerWindowCoordinator(
    private val windowFactory: VisualizerWindowFactory,
) {
    private var openedForAuthenticatedSession = false
    var model: VisualizerModel = VisualizerModel.initial()
        private set

    fun openVisualizer() {
        windowFactory.open()
        windowFactory.applyModel(model)
    }

    fun onSessionStateChanged(sessionState: com.btgun.desktop.control.ControlServerSessionState) {
        model = modelForSessionState(model, sessionState)
        if (sessionState == com.btgun.desktop.control.ControlServerSessionState.AUTHENTICATED && !openedForAuthenticatedSession) {
            openedForAuthenticatedSession = true
            windowFactory.open()
        }
        windowFactory.applyModel(model)
    }

    fun onHapticResultReceived(result: HapticResult) {
        model = model.withHapticResult(result)
        windowFactory.applyModel(model)
    }

    fun onVisualizerStatusReceived(
        status: VisualizerStatus,
        observedElapsedNanos: Long = System.nanoTime(),
    ) {
        model = modelForVisualizerStatus(model, status, observedElapsedNanos)
        windowFactory.applyModel(model)
    }

    companion object {
        fun modelForVisualizerStatus(
            model: VisualizerModel,
            status: VisualizerStatus,
            observedElapsedNanos: Long,
        ): VisualizerModel =
            model.withVisualizerStatus(status = status, observedElapsedNanos = observedElapsedNanos)

        fun modelForSessionState(
            model: VisualizerModel,
            sessionState: com.btgun.desktop.control.ControlServerSessionState,
        ): VisualizerModel =
            when (sessionState) {
                com.btgun.desktop.control.ControlServerSessionState.AUTHENTICATED ->
                    model.withPacketLifecycle(com.btgun.desktop.transport.InputStreamLifecycleState.ACTIVE)
                com.btgun.desktop.control.ControlServerSessionState.DEGRADED ->
                    model.withPacketLifecycle(com.btgun.desktop.transport.InputStreamLifecycleState.STALE)
                com.btgun.desktop.control.ControlServerSessionState.DISCONNECTED,
                com.btgun.desktop.control.ControlServerSessionState.STOPPED,
                -> model.withPacketLifecycle(com.btgun.desktop.transport.InputStreamLifecycleState.STOPPED)
                else -> model
            }
    }
}

data class VisualizerCloseBehavior(
    val disposeVisualizerUi: Boolean,
    val stopControlServer: Boolean,
    val stopBackendRuntimes: Boolean,
)

enum class VisualizerDisplayState {
    WAITING,
    AUTHENTICATED,
    DEGRADED,
    DISCONNECTED,
}

private fun com.btgun.desktop.transport.InputStreamLifecycleState.toDisplayState(): VisualizerDisplayState =
    when (this) {
        com.btgun.desktop.transport.InputStreamLifecycleState.ACTIVE -> VisualizerDisplayState.AUTHENTICATED
        com.btgun.desktop.transport.InputStreamLifecycleState.GRACE,
        com.btgun.desktop.transport.InputStreamLifecycleState.STALE,
        -> VisualizerDisplayState.DEGRADED
        com.btgun.desktop.transport.InputStreamLifecycleState.STOPPED -> VisualizerDisplayState.DISCONNECTED
    }
