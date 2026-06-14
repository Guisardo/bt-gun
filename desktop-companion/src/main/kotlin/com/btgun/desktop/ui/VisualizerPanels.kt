package com.btgun.desktop.ui

import com.btgun.desktop.backend.SemanticControllerState
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

data class VisualizerCrosshairPoint(
    val x: Int,
    val y: Int,
)

data class VisualizerCrosshairSpec(
    val label: String,
    val sizePx: Int,
    val plotInsetPx: Int = 16,
) {
    val minPlotPx: Int = plotInsetPx
    val maxPlotPx: Int = sizePx - plotInsetPx
    val centerPx: Int = sizePx / 2

    fun pointFor(x: Float, y: Float): VisualizerCrosshairPoint {
        val clampedX = x.coerceIn(-1.0f, 1.0f)
        val clampedY = y.coerceIn(-1.0f, 1.0f)
        val halfSpan = (maxPlotPx - minPlotPx) / 2.0f
        return VisualizerCrosshairPoint(
            x = (centerPx + clampedX * halfSpan).toInt(),
            y = (centerPx - clampedY * halfSpan).toInt(),
        )
    }
}

data class VisualizerAimPoint(
    val x: Float,
    val y: Float,
)

object VisualizerPanels {
    private const val BUTTON_MIN_SIZE = 44
    private const val BODY_FONT_SIZE = 14f
    private val buttonLabels = listOf("Trigger", "Reload", "X", "Y", "A", "B")

    fun buttonIndicatorLabels(): List<String> = buttonLabels

    fun stickCrosshairSpec(): VisualizerCrosshairSpec =
        VisualizerCrosshairSpec(label = "Stick", sizePx = 200)

    fun aimCrosshairSpec(): VisualizerCrosshairSpec =
        VisualizerCrosshairSpec(label = "Aim", sizePx = 220)

    fun staleOverlayText(stale: Boolean, disconnected: Boolean): String? =
        when {
            disconnected -> "disconnected"
            stale -> "stale"
            else -> null
        }

    fun displayAimFor(
        currentAimX: Float,
        currentAimY: Float,
        lastAcceptedAimX: Float,
        lastAcceptedAimY: Float,
        stale: Boolean,
    ): VisualizerAimPoint =
        if (stale) {
            VisualizerAimPoint(lastAcceptedAimX, lastAcceptedAimY)
        } else {
            VisualizerAimPoint(currentAimX, currentAimY)
        }

    fun usesLastAcceptedAimWhenStale(
        currentAimX: Float,
        currentAimY: Float,
        lastAcceptedAimX: Float,
        lastAcceptedAimY: Float,
        stale: Boolean,
    ): Boolean {
        val display = displayAimFor(currentAimX, currentAimY, lastAcceptedAimX, lastAcceptedAimY, stale)
        return !stale || (display.x == lastAcceptedAimX && display.y == lastAcceptedAimY)
    }

    fun metricsLabels(snapshot: VisualizerMetricSnapshot): List<String> =
        listOf(
            snapshot.headlineLatencyLabel,
            "Clock offset: ${snapshot.offsetQuality.name.lowercase()}",
            "Capture to send: ${snapshot.captureToSendMillis} ms",
            "Receive to render: ${snapshot.receiveToRenderMillis} ms",
            "Last input: ${snapshot.sampleAgeMillis} ms ago",
            snapshot.packetLossLabel,
        )

    fun eventStripLabels(
        events: List<VisualizerProductEvent>,
        nowElapsedNanos: Long,
    ): List<String> =
        events.take(VisualizerModel.MAX_PRODUCT_EVENTS).map { event ->
            val ageMillis = ((nowElapsedNanos - event.ageSourceElapsedNanos).coerceAtLeast(0L)) / 1_000_000L
            "${event.type} seq=${event.sequence ?: "none"} age=$ageMillis ms"
        }

    fun activeButtonLabels(state: SemanticControllerState): List<String> =
        listOfNotNull(
            "Trigger".takeIf { state.trigger },
            "Reload".takeIf { state.reload },
            "X".takeIf { state.x },
            "Y".takeIf { state.y },
            "A".takeIf { state.a },
            "B".takeIf { state.b },
        )

    fun rawDebugLabels(rawDebug: VisualizerRawDebugState): List<String> {
        if (!rawDebug.enabled) return listOf("Raw debug off")
        val labels = mutableListOf("Raw debug on")
        if (rawDebug.collapsed) return labels
        labels += "Provider: ${rawDebug.provider ?: "unavailable"}"
        labels += "Yaw: ${formatAxis(rawDebug.yaw ?: 0.0f)} | Pitch: ${formatAxis(rawDebug.pitch ?: 0.0f)} | Roll: ${formatAxis(rawDebug.roll ?: 0.0f)}"
        labels += "Raw aim: x=${formatAxis(rawDebug.rawAimX ?: 0.0f)} y=${formatAxis(rawDebug.rawAimY ?: 0.0f)}"
        rawDebug.lastRejection?.let { reason ->
            labels += "Last rejection: ${sanitizeLabel(reason)}"
        }
        return labels
    }

    fun liveGamepadPanel(model: VisualizerModel = VisualizerModel.initial()): LiveGamepadPanel =
        LiveGamepadPanel(model)

    class LiveGamepadPanel(model: VisualizerModel) : JPanel(BorderLayout(12, 12)) {
        private val buttonIndicators = buttonLabels.map { ButtonIndicator(it) }
        private val stickPanel = CrosshairPanel(stickCrosshairSpec())
        private val aimPanel = CrosshairPanel(aimCrosshairSpec())
        private val numericLabels = JLabel("", SwingConstants.CENTER)

        init {
            background = COLOR_SURFACE
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            minimumSize = Dimension(480, 300)
            preferredSize = Dimension(520, 320)

            val buttonGrid = JPanel(GridLayout(2, 3, 8, 8))
            buttonGrid.background = COLOR_SURFACE
            buttonIndicators.forEach(buttonGrid::add)

            val crosshairs = JPanel(GridLayout(1, 2, 16, 0))
            crosshairs.background = COLOR_SURFACE
            crosshairs.add(stickPanel)
            crosshairs.add(aimPanel)

            numericLabels.font = numericLabels.font.deriveFont(Font.PLAIN, BODY_FONT_SIZE)

            add(buttonGrid, BorderLayout.NORTH)
            add(crosshairs, BorderLayout.CENTER)
            add(numericLabels, BorderLayout.SOUTH)
            updateModel(model)
        }

        fun updateModel(model: VisualizerModel) {
            val state = model.liveState
            val activeByLabel = mapOf(
                "Trigger" to state.trigger,
                "Reload" to state.reload,
                "X" to state.x,
                "Y" to state.y,
                "A" to state.a,
                "B" to state.b,
            )
            buttonIndicators.forEach { indicator ->
                indicator.updateActive(activeByLabel[indicator.labelText] == true, state.stale)
            }
            stickPanel.updatePoint(
                x = normalizeStickAxis(state.stickX),
                y = normalizeStickAxis(state.stickY),
                overlayText = staleOverlayText(stale = state.stale, disconnected = model.packetLifecycle.isDisconnected()),
            )
            val aim = displayAimFor(
                currentAimX = state.aimX,
                currentAimY = state.aimY,
                lastAcceptedAimX = model.lastAcceptedAimX,
                lastAcceptedAimY = model.lastAcceptedAimY,
                stale = state.stale,
            )
            aimPanel.updatePoint(
                x = aim.x,
                y = aim.y,
                overlayText = staleOverlayText(stale = state.stale, disconnected = model.packetLifecycle.isDisconnected()),
            )
            val activeButtons = activeButtonLabels(state).ifEmpty { listOf("none") }.joinToString(", ")
            numericLabels.text = "Buttons: $activeButtons | Stick: ${state.stickX}, ${state.stickY} | Aim: ${formatAxis(aim.x)}, ${formatAxis(aim.y)}"
            repaint()
        }
    }

    private class ButtonIndicator(
        val labelText: String,
    ) : JLabel(labelText, SwingConstants.CENTER) {
        init {
            minimumSize = Dimension(BUTTON_MIN_SIZE, BUTTON_MIN_SIZE)
            preferredSize = Dimension(BUTTON_MIN_SIZE, BUTTON_MIN_SIZE)
            isOpaque = true
            border = BorderFactory.createLineBorder(COLOR_BORDER_STRONG)
            font = font.deriveFont(Font.BOLD, 13f)
        }

        fun updateActive(active: Boolean, stale: Boolean) {
            background = when {
                stale -> COLOR_WARNING_BACKGROUND
                active -> COLOR_ACTIVE_BACKGROUND
                else -> COLOR_SURFACE
            }
            foreground = if (active) COLOR_TEXT else COLOR_MUTED_TEXT
            border = BorderFactory.createLineBorder(if (active) COLOR_ACCENT else COLOR_BORDER_STRONG)
            text = if (stale) "$labelText stale" else labelText
        }
    }

    private class CrosshairPanel(
        private val spec: VisualizerCrosshairSpec,
    ) : JPanel() {
        private var point = spec.pointFor(0.0f, 0.0f)
        private var overlayText: String? = null
        private var axisText: String = "x=0.00 y=0.00"

        init {
            preferredSize = Dimension(spec.sizePx, spec.sizePx)
            minimumSize = preferredSize
            background = COLOR_SURFACE
            border = BorderFactory.createTitledBorder(spec.label)
            toolTipText = "${spec.label} crosshair"
        }

        fun updatePoint(x: Float, y: Float, overlayText: String?) {
            point = spec.pointFor(x, y)
            this.overlayText = overlayText
            axisText = "x=${formatAxis(x.coerceIn(-1.0f, 1.0f))} y=${formatAxis(y.coerceIn(-1.0f, 1.0f))}"
            repaint()
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g = graphics as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            g.color = COLOR_BORDER
            g.stroke = BasicStroke(1.5f)
            g.drawLine(spec.centerPx, spec.minPlotPx, spec.centerPx, spec.maxPlotPx)
            g.drawLine(spec.minPlotPx, spec.centerPx, spec.maxPlotPx, spec.centerPx)

            g.color = COLOR_ACCENT
            g.fillOval(point.x - 5, point.y - 5, 10, 10)

            g.color = COLOR_TEXT
            g.font = g.font.deriveFont(Font.PLAIN, 12f)
            g.drawString(axisText, spec.minPlotPx, spec.maxPlotPx + 12)

            val overlay = overlayText ?: return
            g.color = COLOR_OVERLAY
            g.fillRect(spec.minPlotPx, spec.centerPx - 18, spec.maxPlotPx - spec.minPlotPx, 36)
            g.color = COLOR_WARNING_TEXT
            g.font = g.font.deriveFont(Font.BOLD, 14f)
            g.drawString(overlay, spec.centerPx - 44, spec.centerPx + 5)
        }
    }

    private fun normalizeStickAxis(value: Int): Float =
        (value.toFloat() / Short.MAX_VALUE.toFloat()).coerceIn(-1.0f, 1.0f)

    private fun formatAxis(value: Float): String =
        "%.2f".format(java.util.Locale.US, value)

    private fun sanitizeLabel(value: String): String =
        FORBIDDEN_DIAGNOSTIC_TERMS.fold(value) { current, term ->
            current.replace(Regex("(?i)${Regex.escape(term)}[^\\s<]*"), "redacted")
        }.take(80)

    private fun com.btgun.desktop.transport.InputStreamLifecycleState.isDisconnected(): Boolean =
        this == com.btgun.desktop.transport.InputStreamLifecycleState.STOPPED

    private val COLOR_SURFACE = Color.WHITE
    private val COLOR_TEXT = Color(0x18, 0x1F, 0x29)
    private val COLOR_MUTED_TEXT = Color(0x59, 0x65, 0x73)
    private val COLOR_ACCENT = Color(0x0C, 0x5D, 0x80)
    private val COLOR_BORDER = Color(0xDC, 0xE4, 0xE8)
    private val COLOR_BORDER_STRONG = Color(0xBA, 0xC7, 0xCF)
    private val COLOR_ACTIVE_BACKGROUND = Color(0xE0, 0xF3, 0xF8)
    private val COLOR_WARNING_BACKGROUND = Color(0xFF, 0xF4, 0xE8)
    private val COLOR_WARNING_TEXT = Color(0xB3, 0x50, 0x00)
    private val COLOR_OVERLAY = Color(0xFF, 0xF4, 0xE8, 210)
    private val FORBIDDEN_DIAGNOSTIC_TERMS = listOf(
        "sec" + "ret",
        "hm" + "ac",
        "private" + " key",
        "pairing" + " material",
        "stream" + " key",
    )
}
