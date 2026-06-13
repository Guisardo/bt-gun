package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.HapticSendResult
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.control.VisualizerStatus
import com.btgun.desktop.backend.macos.MacosBackendRuntimeDiagnostics
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeDiagnostics
import com.btgun.desktop.haptics.HapticCommand
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.haptics.HapticResultStatus
import com.btgun.desktop.transport.InputReplayRejectReason
import com.btgun.desktop.transport.InputStreamLifecycleState
import com.btgun.desktop.transport.UdpReceivedInput
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
    private val confirmObservedAction = JButton(confirmObservedLabel())
    private val confirmLimitationAction = JButton(confirmLimitationLabel())
    private val resetChecklistAction = JButton(resetChecklistLabel())
    private val events = JLabel("Recent product events: none")
    private val rawDebug = JLabel("Raw debug off")
    private var currentModel = VisualizerModel.initial()
    private var resetArmed = false

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
        confirmObservedAction.addActionListener {
            currentModel = currentModel.confirmNextObservedRow()
            resetArmed = false
            applyModel(currentModel)
        }
        confirmLimitationAction.addActionListener {
            currentModel = currentModel.confirmLimitation(VisualizerChecklistRowId.MACOS_HID_HAPTIC_LIMIT)
            resetArmed = false
            applyModel(currentModel)
        }
        resetChecklistAction.toolTipText = resetChecklistConfirmationCopy()
        resetChecklistAction.addActionListener {
            if (resetArmed) {
                currentModel = currentModel.resetChecklist()
                resetArmed = false
                applyModel(currentModel)
            } else {
                resetArmed = true
                session.text = resetChecklistConfirmationCopy()
            }
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
            summary.text = model.topSummaryLabel()
            session.text = summaryFor(model.packetLifecycle.toDisplayState())
            profile.text = "Profile: ${model.profileSummary.displayName}"
            checklist.text = checklistHtml(model.checklistRows)
            gamepad.updateModel(model)
            metrics.text = labelsHtml(VisualizerPanels.metricsLabels(model.metrics))
            recenter.text = labelsHtml(recenterStatusLabels(model))
            hapticAction.isEnabled = controlServer != null && hapticButtonEnabled(model.controlSessionState)
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
        val checklistActions = JPanel(GridLayout(1, 3, SPACING_SM, 0))
        checklistActions.background = COLOR_BACKGROUND
        checklistActions.add(confirmObservedAction)
        checklistActions.add(confirmLimitationAction)
        checklistActions.add(resetChecklistAction)
        south.add(checklistActions)
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

        fun topSummaryPassing(): String = "Phase 9 checks passing"

        fun topSummaryFailed(): String = "Phase 9 checks need attention"

        fun hapticButtonLabel(): String = "Run phone haptic test"

        fun confirmObservedLabel(): String = "Confirm observed"

        fun confirmLimitationLabel(): String = "Confirm limitation"

        fun resetChecklistLabel(): String = "Reset checklist"

        fun resetChecklistConfirmationCopy(): String =
            "Reset checklist: Reset Phase 9 checklist progress for this session? Live input and pairing state are unchanged."

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

        fun backendProofLabels(
            windowsDiagnostics: WindowsBackendRuntimeDiagnostics?,
            macosDiagnostics: MacosBackendRuntimeDiagnostics?,
        ): List<String> =
            listOfNotNull(
                windowsDiagnostics?.let { diagnostics ->
                    val sequence = diagnostics.lastSourceSequence?.let { " seq=$it" }.orEmpty()
                    "Phase 6 Windows VHF: lifecycle=${diagnostics.lifecycleState.name.lowercase()}$sequence routed=${diagnostics.outputHapticCommandsRouted}"
                },
                macosDiagnostics?.let {
                    "macOS HID haptic unsupported/deferred; LAN and Windows phone haptics remain available."
                },
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
    private val metrics: VisualizerMetrics = VisualizerMetrics(),
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

    fun onProfileMetadataReceived(metadata: ProfileMetadata) {
        model = model.withProfileMetadata(metadata)
        windowFactory.applyModel(model)
    }

    fun onUdpInputReceived(
        input: UdpReceivedInput,
        observedElapsedNanos: Long = System.nanoTime(),
    ) {
        val snapshot = metrics.record(input = input, desktopRenderElapsedNanos = observedElapsedNanos)
        model = model
            .withAcceptedInput(input = input, observedElapsedNanos = observedElapsedNanos)
            .withMetrics(snapshot)
        windowFactory.applyModel(model)
    }

    fun onUdpInputRejected(reason: InputReplayRejectReason) {
        model = model.withInputRejection(reason.name.lowercase())
        windowFactory.applyModel(model)
    }

    fun onUdpInputStateChanged(state: InputStreamLifecycleState) {
        model = model.withPacketLifecycle(state)
        windowFactory.applyModel(model)
    }

    fun onVisualizerStatusReceived(
        status: VisualizerStatus,
        observedElapsedNanos: Long = System.nanoTime(),
    ) {
        metrics.recordStatus(status = status, desktopReceivedElapsedNanos = observedElapsedNanos)
        model = modelForVisualizerStatus(model, status, observedElapsedNanos)
            .withMetrics(metrics.snapshot())
        windowFactory.applyModel(model)
    }

    fun onWindowsBackendDiagnosticsChanged(
        diagnostics: WindowsBackendRuntimeDiagnostics,
        observedElapsedNanos: Long = System.nanoTime(),
    ) {
        model = modelForWindowsBackendDiagnostics(model, diagnostics, observedElapsedNanos)
        windowFactory.applyModel(model)
    }

    fun onMacosBackendDiagnosticsChanged(
        diagnostics: MacosBackendRuntimeDiagnostics,
        observedElapsedNanos: Long = System.nanoTime(),
    ) {
        model = modelForMacosBackendDiagnostics(model, diagnostics, observedElapsedNanos)
        windowFactory.applyModel(model)
    }

    companion object {
        fun modelForVisualizerStatus(
            model: VisualizerModel,
            status: VisualizerStatus,
            observedElapsedNanos: Long,
        ): VisualizerModel =
            model.withVisualizerStatus(status = status, observedElapsedNanos = observedElapsedNanos)

        fun modelForWindowsBackendDiagnostics(
            model: VisualizerModel,
            diagnostics: WindowsBackendRuntimeDiagnostics,
            observedElapsedNanos: Long,
        ): VisualizerModel =
            model.withWindowsBackendDiagnostics(
                diagnostics = diagnostics,
                observedElapsedNanos = observedElapsedNanos,
            )

        fun modelForMacosBackendDiagnostics(
            model: VisualizerModel,
            diagnostics: MacosBackendRuntimeDiagnostics,
            observedElapsedNanos: Long,
        ): VisualizerModel =
            model.withMacosBackendDiagnostics(
                diagnostics = diagnostics,
                observedElapsedNanos = observedElapsedNanos,
            )

        fun modelForSessionState(
            model: VisualizerModel,
            sessionState: com.btgun.desktop.control.ControlServerSessionState,
        ): VisualizerModel =
            model.withControlSessionState(sessionState)
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
