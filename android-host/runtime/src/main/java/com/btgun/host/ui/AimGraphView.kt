package com.btgun.host.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.btgun.host.motion.AimCalibrationMark
import kotlin.math.min

class AimGraphView(context: Context) : View(context) {
    private var graphState: DashboardAimGraph = DashboardAimGraph(
        x = 0f,
        y = 0f,
        enabled = false,
        calibrated = false,
        statusLabel = "Calibration idle",
        activeMark = null,
        capturedMarks = emptyList(),
        latencyMillis = null,
    )
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(78, 93, 108)
        strokeWidth = dp(1.5f)
        style = Paint.Style.STROKE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 41, 51)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 118, 92)
        style = Paint.Style.FILL
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(179, 80, 0)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }
    private val capturedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(52, 91, 178)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 41, 51)
        textSize = dp(13f)
    }

    fun render(state: DashboardAimGraph) {
        graphState = state
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(220f).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(18f)
        val topText = dp(22f)
        val availableWidth = width - pad * 2f
        val availableHeight = height - topText - pad * 1.5f
        val size = min(availableWidth, availableHeight)
        val left = pad + (availableWidth - size) / 2f
        val top = topText
        val rect = RectF(left, top, left + size, top + size)

        canvas.drawText(statusText(), pad, dp(15f), textPaint)
        canvas.drawRect(rect, borderPaint)
        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.drawLine(rect.left, cy, rect.right, cy, axisPaint)
        canvas.drawLine(cx, rect.top, cx, rect.bottom, axisPaint)

        graphState.capturedMarks.forEach { mark ->
            val point = mark.toScreen(rect)
            canvas.drawCircle(point.first, point.second, dp(4f), capturedPaint)
        }
        graphState.activeMark?.let { mark ->
            val point = mark.toScreen(rect)
            canvas.drawCircle(point.first, point.second, dp(10f), targetPaint)
        }
        if (graphState.enabled) {
            val x = rect.left + ((graphState.x.coerceIn(-1f, 1f) + 1f) / 2f) * rect.width()
            val y = rect.top + ((1f - graphState.y.coerceIn(-1f, 1f)) / 2f) * rect.height()
            canvas.drawCircle(x, y, dp(7f), dotPaint)
        }
    }

    private fun AimCalibrationMark.toScreen(rect: RectF): Pair<Float, Float> {
        val x = rect.left + ((target.x + 1f) / 2f) * rect.width()
        val y = rect.top + ((1f - target.y) / 2f) * rect.height()
        return x to y
    }

    private fun statusText(): String {
        val mode = if (graphState.calibrated) "calibrated" else "uncalibrated"
        val latency = graphState.latencyMillis?.let { " | ${it}ms" }.orEmpty()
        return "${graphState.statusLabel} | $mode$latency"
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
