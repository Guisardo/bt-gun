package com.btgun.desktop.backend.macos

import com.btgun.desktop.backend.BackendCapabilities
import com.btgun.desktop.backend.BackendCapabilityPresets
import com.btgun.desktop.backend.BackendLifecycleResult
import com.btgun.desktop.backend.BackendLifecycleState
import com.btgun.desktop.backend.BackendPublishResult
import com.btgun.desktop.backend.SemanticControllerState
import com.btgun.desktop.backend.SimulatedOutputReport
import com.btgun.desktop.backend.VirtualControllerBackend
import com.btgun.desktop.backend.VirtualControllerDescriptor
import com.btgun.desktop.backend.btGunV1Descriptor
import com.btgun.desktop.backend.requireBtGunV1Invariant
import com.btgun.desktop.haptics.HapticCommand

class MacosVirtualControllerBackend(
    private val helper: MacosHidHelperClient = MacosHidHelper(),
    override val descriptor: VirtualControllerDescriptor = btGunV1Descriptor,
) : VirtualControllerBackend {
    private val lock = Any()
    private var state: SemanticControllerState = SemanticControllerState()
    private var lifecycle: BackendLifecycleState = BackendLifecycleState.STOPPED
    private var lastResult: BackendPublishResult? = null
    private var outputReportCounter: Long = 0L
    private var simulatedOutputReportCounter: Long = 0L

    init {
        requireBtGunV1Invariant(descriptor)
    }

    override val capabilities: BackendCapabilities
        get() = synchronized(lock) {
            BackendCapabilityPresets.macosCoreHid(helper.readStatus())
        }

    override val lifecycleState: BackendLifecycleState
        get() = synchronized(lock) { lifecycle }

    override val currentState: SemanticControllerState
        get() = synchronized(lock) { state }

    override val lastPublishResult: BackendPublishResult?
        get() = synchronized(lock) { lastResult }

    override fun start(): BackendLifecycleResult =
        synchronized(lock) {
            lifecycle = BackendLifecycleState.STARTED
            BackendLifecycleResult.Started
        }

    override fun publish(state: SemanticControllerState): BackendPublishResult =
        synchronized(lock) {
            val result = if (lifecycle != BackendLifecycleState.STARTED) {
                BackendPublishResult.Rejected("backend not started")
            } else {
                val report = MacosHidReportPacker.packInputReport(state)
                when (val helperResult = helper.submitInputReport(report)) {
                    MacosHidHelperResult.Ok -> {
                        this.state = state
                        BackendPublishResult.Published
                    }
                    is MacosHidHelperResult.Error -> BackendPublishResult.Rejected(helperResult.detail)
                }
            }
            lastResult = result
            result
        }

    fun drainOutputHaptics(nowElapsedNanos: Long): List<HapticCommand> =
        synchronized(lock) {
            require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
            if (lifecycle != BackendLifecycleState.STARTED) return@synchronized emptyList()

            val commands = mutableListOf<HapticCommand>()
            while (true) {
                val report = helper.readOutputReport() ?: break
                outputReportCounter += 1L
                MacosOutputReportMapper.toHapticCommand(
                    reportBytes = report,
                    commandId = "macos-output-report-$outputReportCounter",
                )?.let(commands::add)
            }
            commands
        }

    override fun simulateOutputReport(report: SimulatedOutputReport): HapticCommand? =
        synchronized(lock) {
            if (report.pattern != null) return@synchronized null
            simulatedOutputReportCounter += 1L
            MacosOutputReportMapper.toHapticCommand(
                reportBytes = report.toMacosOutputReportBytes(),
                commandId = "macos-output-report-simulated-$simulatedOutputReportCounter",
            )
        }

    override fun stop(reason: String): BackendLifecycleResult =
        synchronized(lock) {
            lifecycle = BackendLifecycleState.STOPPED
            helper.close()
            BackendLifecycleResult.Stopped(reason)
        }

    private fun SimulatedOutputReport.toMacosOutputReportBytes(): ByteArray {
        val strengthByte = (strength * 255.0).toInt().coerceIn(0, 255)
        return byteArrayOf(
            MACOS_OUTPUT_REPORT_ID.toByte(),
            MACOS_OUTPUT_REPORT_VERSION.toByte(),
            strengthByte.toByte(),
            (durationMs.toInt() and 0xff).toByte(),
            ((durationMs.toInt() ushr 8) and 0xff).toByte(),
            (ttlMs.toInt() and 0xff).toByte(),
            ((ttlMs.toInt() ushr 8) and 0xff).toByte(),
            0,
            0,
        )
    }
}
