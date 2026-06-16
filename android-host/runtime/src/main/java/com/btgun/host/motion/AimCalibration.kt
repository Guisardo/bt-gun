package com.btgun.host.motion

import android.content.Context
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class RawAimPoint(
    val xDegrees: Float,
    val yDegrees: Float,
) {
    operator fun plus(other: RawAimPoint): RawAimPoint =
        RawAimPoint(xDegrees + other.xDegrees, yDegrees + other.yDegrees)

    operator fun minus(other: RawAimPoint): RawAimPoint =
        RawAimPoint(xDegrees - other.xDegrees, yDegrees - other.yDegrees)

    fun distanceTo(other: RawAimPoint): Double =
        hypot((xDegrees - other.xDegrees).toDouble(), (yDegrees - other.yDegrees).toDouble())
}

data class NormalizedAimPoint(
    val x: Float,
    val y: Float,
)

enum class AimCalibrationMark(
    val wireName: String,
    val target: NormalizedAimPoint,
) {
    TOP_LEFT("top_left", NormalizedAimPoint(-1f, 1f)),
    TOP_RIGHT("top_right", NormalizedAimPoint(1f, 1f)),
    BOTTOM_LEFT("bottom_left", NormalizedAimPoint(-1f, -1f)),
    BOTTOM_RIGHT("bottom_right", NormalizedAimPoint(1f, -1f));

    companion object {
        val captureOrder: List<AimCalibrationMark> = listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)
        val polygonOrder: List<AimCalibrationMark> = listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT)

        fun fromWireName(value: String): AimCalibrationMark? =
            entries.firstOrNull { mark -> mark.wireName == value }
    }
}

data class CapturedAimPoint(
    val mark: AimCalibrationMark,
    val rawPoint: RawAimPoint,
    val elapsedRealtimeNanos: Long,
)

data class AimCalibration(
    val providerName: String,
    val centeredPoints: Map<AimCalibrationMark, RawAimPoint>,
    val homography: AimHomography,
    val createdAtEpochMillis: Long,
) {
    fun map(relativeRaw: RawAimPoint): NormalizedAimPoint {
        val projected = homography.project(relativeRaw) ?: return NormalizedAimPoint(0f, 0f)
        return NormalizedAimPoint(
            x = projected.x.coerceIn(-1f, 1f),
            y = projected.y.coerceIn(-1f, 1f),
        )
    }
}

sealed interface AimCalibrationBuildResult {
    data class Valid(
        val calibration: AimCalibration,
        val rawCenter: RawAimPoint,
    ) : AimCalibrationBuildResult

    data class Invalid(val reason: String) : AimCalibrationBuildResult
}

object AimCalibrationSolver {
    const val MIN_POINT_DISTANCE_DEGREES: Double = 2.0
    const val MIN_RAW_AREA_DEGREES: Double = 25.0
    const val MIN_HOMOGRAPHY_DETERMINANT: Double = 1e-6
    const val MAX_REPROJECTION_ERROR: Double = 0.05

    fun buildFromCaptured(
        providerName: String,
        capturedPoints: List<CapturedAimPoint>,
        createdAtEpochMillis: Long,
    ): AimCalibrationBuildResult {
        val rawByMark = capturedPoints.associate { point -> point.mark to point.rawPoint }
        val missing = AimCalibrationMark.captureOrder.firstOrNull { mark -> rawByMark[mark] == null }
        if (missing != null) {
            return AimCalibrationBuildResult.Invalid("missing ${missing.wireName}")
        }
        validateRawPoints(rawByMark)?.let { reason ->
            return AimCalibrationBuildResult.Invalid(reason)
        }

        val rawToTarget = AimHomography.fromPoints(rawByMark, targetPoints())
            ?: return AimCalibrationBuildResult.Invalid("homography solve failed")
        if (abs(rawToTarget.determinant()) < MIN_HOMOGRAPHY_DETERMINANT) {
            return AimCalibrationBuildResult.Invalid("homography determinant too small")
        }
        val rawCenter = rawToTarget.inverse()?.project(NormalizedAimPoint(0f, 0f))
            ?: return AimCalibrationBuildResult.Invalid("raw center projection failed")
        val center = RawAimPoint(rawCenter.x, rawCenter.y)
        val centered = rawByMark.mapValues { (_, point) -> point - center }
        return buildFromCentered(
            providerName = providerName,
            centeredPoints = centered,
            createdAtEpochMillis = createdAtEpochMillis,
            rawCenter = center,
        )
    }

    fun buildFromCentered(
        providerName: String,
        centeredPoints: Map<AimCalibrationMark, RawAimPoint>,
        createdAtEpochMillis: Long,
        rawCenter: RawAimPoint = RawAimPoint(0f, 0f),
    ): AimCalibrationBuildResult {
        val missing = AimCalibrationMark.captureOrder.firstOrNull { mark -> centeredPoints[mark] == null }
        if (missing != null) {
            return AimCalibrationBuildResult.Invalid("missing ${missing.wireName}")
        }
        validateRawPoints(centeredPoints)?.let { reason ->
            return AimCalibrationBuildResult.Invalid(reason)
        }

        val homography = AimHomography.fromPoints(centeredPoints, targetPoints())
            ?: return AimCalibrationBuildResult.Invalid("homography solve failed")
        if (abs(homography.determinant()) < MIN_HOMOGRAPHY_DETERMINANT) {
            return AimCalibrationBuildResult.Invalid("homography determinant too small")
        }
        val maxError = maxReprojectionError(homography, centeredPoints)
        if (maxError > MAX_REPROJECTION_ERROR) {
            return AimCalibrationBuildResult.Invalid("reprojection error ${format(maxError)}")
        }
        return AimCalibrationBuildResult.Valid(
            calibration = AimCalibration(
                providerName = providerName,
                centeredPoints = centeredPoints,
                homography = homography,
                createdAtEpochMillis = createdAtEpochMillis,
            ),
            rawCenter = rawCenter,
        )
    }

    private fun targetPoints(): Map<AimCalibrationMark, NormalizedAimPoint> =
        AimCalibrationMark.entries.associateWith { mark -> mark.target }

    private fun validateRawPoints(points: Map<AimCalibrationMark, RawAimPoint>): String? {
        val ordered = AimCalibrationMark.polygonOrder.map { mark -> points.getValue(mark) }
        ordered.forEachIndexed { index, point ->
            ordered.drop(index + 1).forEach { other ->
                if (point.distanceTo(other) < MIN_POINT_DISTANCE_DEGREES) {
                    return "points too close"
                }
            }
        }
        if (isSelfCrossed(ordered)) {
            return "quad crossed"
        }
        if (abs(polygonArea(ordered)) < MIN_RAW_AREA_DEGREES) {
            return "quad area too small"
        }
        return null
    }

    private fun isSelfCrossed(points: List<RawAimPoint>): Boolean =
        segmentsIntersect(points[0], points[1], points[2], points[3]) ||
            segmentsIntersect(points[1], points[2], points[3], points[0])

    private fun segmentsIntersect(a: RawAimPoint, b: RawAimPoint, c: RawAimPoint, d: RawAimPoint): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)
        return o1 * o2 < 0.0 && o3 * o4 < 0.0
    }

    private fun orientation(a: RawAimPoint, b: RawAimPoint, c: RawAimPoint): Double =
        (b.xDegrees - a.xDegrees).toDouble() * (c.yDegrees - a.yDegrees).toDouble() -
            (b.yDegrees - a.yDegrees).toDouble() * (c.xDegrees - a.xDegrees).toDouble()

    private fun polygonArea(points: List<RawAimPoint>): Double {
        var area = 0.0
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % points.size]
            area += point.xDegrees * next.yDegrees - next.xDegrees * point.yDegrees
        }
        return area / 2.0
    }

    private fun maxReprojectionError(
        homography: AimHomography,
        points: Map<AimCalibrationMark, RawAimPoint>,
    ): Double =
        AimCalibrationMark.entries.maxOf { mark ->
            val projected = homography.project(points.getValue(mark)) ?: return Double.MAX_VALUE
            val target = mark.target
            hypot((projected.x - target.x).toDouble(), (projected.y - target.y).toDouble())
        }

    private fun format(value: Double): String =
        "%.4f".format(value)
}

class AimHomography private constructor(
    private val values: DoubleArray,
) {
    fun project(point: RawAimPoint): NormalizedAimPoint? {
        val denominator = values[6] * point.xDegrees + values[7] * point.yDegrees + values[8]
        if (abs(denominator) < 1e-9) {
            return null
        }
        val x = (values[0] * point.xDegrees + values[1] * point.yDegrees + values[2]) / denominator
        val y = (values[3] * point.xDegrees + values[4] * point.yDegrees + values[5]) / denominator
        return NormalizedAimPoint(x.toFloat(), y.toFloat())
    }

    fun project(point: NormalizedAimPoint): NormalizedAimPoint? {
        val denominator = values[6] * point.x + values[7] * point.y + values[8]
        if (abs(denominator) < 1e-9) {
            return null
        }
        val x = (values[0] * point.x + values[1] * point.y + values[2]) / denominator
        val y = (values[3] * point.x + values[4] * point.y + values[5]) / denominator
        return NormalizedAimPoint(x.toFloat(), y.toFloat())
    }

    fun determinant(): Double =
        values[0] * (values[4] * values[8] - values[5] * values[7]) -
            values[1] * (values[3] * values[8] - values[5] * values[6]) +
            values[2] * (values[3] * values[7] - values[4] * values[6])

    fun inverse(): AimHomography? {
        val det = determinant()
        if (abs(det) < 1e-9) {
            return null
        }
        val m = values
        val inv = doubleArrayOf(
            (m[4] * m[8] - m[5] * m[7]) / det,
            (m[2] * m[7] - m[1] * m[8]) / det,
            (m[1] * m[5] - m[2] * m[4]) / det,
            (m[5] * m[6] - m[3] * m[8]) / det,
            (m[0] * m[8] - m[2] * m[6]) / det,
            (m[2] * m[3] - m[0] * m[5]) / det,
            (m[3] * m[7] - m[4] * m[6]) / det,
            (m[1] * m[6] - m[0] * m[7]) / det,
            (m[0] * m[4] - m[1] * m[3]) / det,
        )
        return AimHomography(inv)
    }

    companion object {
        fun fromPoints(
            rawPoints: Map<AimCalibrationMark, RawAimPoint>,
            targetPoints: Map<AimCalibrationMark, NormalizedAimPoint>,
        ): AimHomography? {
            val matrix = Array(8) { DoubleArray(8) }
            val target = DoubleArray(8)
            AimCalibrationMark.captureOrder.forEachIndexed { index, mark ->
                val raw = rawPoints.getValue(mark)
                val normalized = targetPoints.getValue(mark)
                val row = index * 2
                matrix[row][0] = raw.xDegrees.toDouble()
                matrix[row][1] = raw.yDegrees.toDouble()
                matrix[row][2] = 1.0
                matrix[row][6] = -normalized.x * raw.xDegrees.toDouble()
                matrix[row][7] = -normalized.x * raw.yDegrees.toDouble()
                target[row] = normalized.x.toDouble()

                matrix[row + 1][3] = raw.xDegrees.toDouble()
                matrix[row + 1][4] = raw.yDegrees.toDouble()
                matrix[row + 1][5] = 1.0
                matrix[row + 1][6] = -normalized.y * raw.xDegrees.toDouble()
                matrix[row + 1][7] = -normalized.y * raw.yDegrees.toDouble()
                target[row + 1] = normalized.y.toDouble()
            }
            val solution = solveLinear(matrix, target) ?: return null
            return AimHomography(
                doubleArrayOf(
                    solution[0],
                    solution[1],
                    solution[2],
                    solution[3],
                    solution[4],
                    solution[5],
                    solution[6],
                    solution[7],
                    1.0,
                ),
            )
        }

        private fun solveLinear(matrix: Array<DoubleArray>, target: DoubleArray): DoubleArray? {
            val n = target.size
            val augmented = Array(n) { row -> DoubleArray(n + 1) { col -> if (col == n) target[row] else matrix[row][col] } }
            for (col in 0 until n) {
                var pivotRow = col
                for (row in col + 1 until n) {
                    if (abs(augmented[row][col]) > abs(augmented[pivotRow][col])) {
                        pivotRow = row
                    }
                }
                if (abs(augmented[pivotRow][col]) < 1e-9) {
                    return null
                }
                if (pivotRow != col) {
                    val tmp = augmented[col]
                    augmented[col] = augmented[pivotRow]
                    augmented[pivotRow] = tmp
                }
                val pivot = augmented[col][col]
                for (j in col..n) {
                    augmented[col][j] /= pivot
                }
                for (row in 0 until n) {
                    if (row == col) {
                        continue
                    }
                    val factor = augmented[row][col]
                    for (j in col..n) {
                        augmented[row][j] -= factor * augmented[col][j]
                    }
                }
            }
            return DoubleArray(n) { row -> augmented[row][n] }
        }
    }
}

class RawAimTracker {
    private var lastX: Float? = null
    private var lastY: Float? = null
    private var xOffset: Float = 0f
    private var yOffset: Float = 0f

    fun reset() {
        lastX = null
        lastY = null
        xOffset = 0f
        yOffset = 0f
    }

    fun track(sample: com.btgun.host.model.MotionSample): RawAimPoint {
        val x = unwrap(sample.yaw, lastX) { offset -> xOffset += offset }.also { lastX = sample.yaw }
        val y = unwrap(sample.pitch, lastY) { offset -> yOffset += offset }.also { lastY = sample.pitch }
        return RawAimPoint(xDegrees = x + xOffset, yDegrees = y + yOffset)
    }

    private fun unwrap(current: Float, previous: Float?, addOffset: (Float) -> Unit): Float {
        if (previous != null) {
            val delta = current - previous
            if (delta > 180f) {
                addOffset(-360f)
            } else if (delta < -180f) {
                addOffset(360f)
            }
        }
        return current
    }
}

enum class AimCalibrationMode {
    IDLE,
    WAITING_FOR_MARK,
    CAPTURED,
    VALIDATING,
    ACTIVE,
    FAILED,
}

data class AimCalibrationState(
    val mode: AimCalibrationMode = AimCalibrationMode.IDLE,
    val activeMark: AimCalibrationMark? = null,
    val capturedPoints: List<CapturedAimPoint> = emptyList(),
    val statusLabel: String = "Calibration idle",
    val error: String? = null,
    val activeCalibration: AimCalibration? = null,
) {
    val isCaptureActive: Boolean
        get() = mode == AimCalibrationMode.WAITING_FOR_MARK || mode == AimCalibrationMode.FAILED

    val isCalibrated: Boolean
        get() = activeCalibration != null
}

sealed interface AimCalibrationCaptureOutcome {
    data class Captured(val nextMark: AimCalibrationMark?) : AimCalibrationCaptureOutcome
    data class Completed(val calibration: AimCalibration, val rawCenter: RawAimPoint) : AimCalibrationCaptureOutcome
    data class Failed(val reason: String) : AimCalibrationCaptureOutcome
    data object Ignored : AimCalibrationCaptureOutcome
}

class AimCalibrationSession {
    var state: AimCalibrationState = AimCalibrationState()
        private set

    fun setActiveCalibration(calibration: AimCalibration?) {
        state = if (calibration == null) {
            AimCalibrationState()
        } else {
            AimCalibrationState(
                mode = AimCalibrationMode.ACTIVE,
                statusLabel = "Calibrated aim active",
                activeCalibration = calibration,
            )
        }
    }

    fun start() {
        state = AimCalibrationState(
            mode = AimCalibrationMode.WAITING_FOR_MARK,
            activeMark = AimCalibrationMark.TOP_LEFT,
            statusLabel = "Aim at top-left, press trigger",
            activeCalibration = state.activeCalibration,
        )
    }

    fun capture(
        providerName: String,
        rawPoint: RawAimPoint,
        elapsedRealtimeNanos: Long,
        createdAtEpochMillis: Long,
    ): AimCalibrationCaptureOutcome {
        val mark = state.activeMark ?: return AimCalibrationCaptureOutcome.Ignored
        if (!state.isCaptureActive) {
            return AimCalibrationCaptureOutcome.Ignored
        }
        val captured = state.capturedPoints + CapturedAimPoint(mark, rawPoint, elapsedRealtimeNanos)
        val next = AimCalibrationMark.captureOrder.getOrNull(captured.size)
        if (next != null) {
            state = state.copy(
                mode = AimCalibrationMode.WAITING_FOR_MARK,
                activeMark = next,
                capturedPoints = captured,
                statusLabel = "Aim at ${next.wireName.replace('_', '-')}, press trigger",
                error = null,
            )
            return AimCalibrationCaptureOutcome.Captured(next)
        }

        state = state.copy(mode = AimCalibrationMode.VALIDATING, capturedPoints = captured, statusLabel = "Validating calibration")
        return when (val result = AimCalibrationSolver.buildFromCaptured(providerName, captured, createdAtEpochMillis)) {
            is AimCalibrationBuildResult.Valid -> {
                state = AimCalibrationState(
                    mode = AimCalibrationMode.ACTIVE,
                    statusLabel = "Calibrated aim active",
                    activeCalibration = result.calibration,
                )
                AimCalibrationCaptureOutcome.Completed(result.calibration, result.rawCenter)
            }
            is AimCalibrationBuildResult.Invalid -> {
                state = AimCalibrationState(
                    mode = AimCalibrationMode.FAILED,
                    activeMark = AimCalibrationMark.TOP_LEFT,
                    statusLabel = "Calibration failed: ${result.reason}. Aim at top-left, press trigger",
                    error = result.reason,
                    activeCalibration = state.activeCalibration,
                )
                AimCalibrationCaptureOutcome.Failed(result.reason)
            }
        }
    }

    fun cancelInProgress() {
        setActiveCalibration(state.activeCalibration)
    }
}

object AimCalibrationCodec {
    private const val VERSION = "v1"
    private const val FIELD_SEPARATOR = "|"
    private const val POINT_SEPARATOR = ","

    fun encode(calibration: AimCalibration): String =
        buildList {
            add(VERSION)
            add(calibration.providerName)
            add(calibration.createdAtEpochMillis.toString())
            AimCalibrationMark.captureOrder.forEach { mark ->
                val point = calibration.centeredPoints.getValue(mark)
                add("${point.xDegrees}$POINT_SEPARATOR${point.yDegrees}")
            }
        }.joinToString(FIELD_SEPARATOR)

    fun decode(encoded: String?): AimCalibration? {
        if (encoded.isNullOrBlank()) {
            return null
        }
        val fields = encoded.split(FIELD_SEPARATOR)
        if (fields.size != 7 || fields[0] != VERSION) {
            return null
        }
        val createdAt = fields[2].toLongOrNull() ?: return null
        val points = mutableMapOf<AimCalibrationMark, RawAimPoint>()
        AimCalibrationMark.captureOrder.forEachIndexed { index, mark ->
            val parts = fields[index + 3].split(POINT_SEPARATOR)
            if (parts.size != 2) {
                return null
            }
            val x = parts[0].toFloatOrNull() ?: return null
            val y = parts[1].toFloatOrNull() ?: return null
            points[mark] = RawAimPoint(x, y)
        }
        return when (
            val result = AimCalibrationSolver.buildFromCentered(
                providerName = fields[1],
                centeredPoints = points,
                createdAtEpochMillis = createdAt,
            )
        ) {
            is AimCalibrationBuildResult.Valid -> result.calibration
            is AimCalibrationBuildResult.Invalid -> null
        }
    }
}

class AimCalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AimCalibration? =
        AimCalibrationCodec.decode(loadEncoded())

    fun loadEncoded(): String? =
        preferences.getString(KEY_CALIBRATION, null)

    fun save(calibration: AimCalibration) {
        preferences.edit()
            .putString(KEY_CALIBRATION, AimCalibrationCodec.encode(calibration))
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_CALIBRATION)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "bt_gun_aim_calibration"
        private const val KEY_CALIBRATION = "active_calibration"
    }
}

fun fallbackAim(raw: RawAimPoint): NormalizedAimPoint =
    NormalizedAimPoint(
        x = clampUnit(raw.xDegrees / FALLBACK_DEGREES_TO_EDGE),
        y = clampUnit(-raw.yDegrees / FALLBACK_DEGREES_TO_EDGE),
    )

private fun clampUnit(value: Float): Float =
    max(-1f, min(1f, value))

private const val FALLBACK_DEGREES_TO_EDGE = 45f
