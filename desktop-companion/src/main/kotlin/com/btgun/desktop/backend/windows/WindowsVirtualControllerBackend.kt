package com.btgun.desktop.backend.windows

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

class WindowsVirtualControllerBackend(
    private val bridge: WindowsDriverBridgeClient = WindowsDriverBridge(),
    override val descriptor: VirtualControllerDescriptor = btGunV1Descriptor,
) : VirtualControllerBackend {
    private val maxOutputReportsPerDrain = 16
    private val lock = Any()
    private var state: SemanticControllerState = SemanticControllerState()
    private var lifecycle: BackendLifecycleState = BackendLifecycleState.STOPPED
    private var lastResult: BackendPublishResult? = null
    private var bridgeStatus: WindowsDriverBridgeStatus? = null
    private var outputReportCounter: Long = 0L
    private var simulatedOutputReportCounter: Long = 0L

    init {
        requireBtGunV1Invariant(descriptor)
    }

    override val capabilities: BackendCapabilities =
        BackendCapabilityPresets.windowsVhf()

    override val lifecycleState: BackendLifecycleState
        get() = synchronized(lock) { lifecycle }

    override val currentState: SemanticControllerState
        get() = synchronized(lock) { state }

    override val lastPublishResult: BackendPublishResult?
        get() = synchronized(lock) { lastResult }

    val lastBridgeStatus: WindowsDriverBridgeStatus?
        get() = synchronized(lock) { bridgeStatus }

    override fun start(): BackendLifecycleResult {
        val status = bridge.readStatus()
        val result = if (status.driverStarted && status.vhfStarted) {
            BackendLifecycleResult.Started
        } else {
            BackendLifecycleResult.Stopped(
                "windows bridge not ready: driverStarted=${status.driverStarted} vhfStarted=${status.vhfStarted}",
            )
        }
        synchronized(lock) {
            bridgeStatus = status
            lifecycle = when (result) {
                BackendLifecycleResult.Started -> BackendLifecycleState.STARTED
                is BackendLifecycleResult.Stopped -> BackendLifecycleState.STOPPED
            }
        }
        return result
    }

    override fun publish(state: SemanticControllerState): BackendPublishResult {
        if (lifecycleState != BackendLifecycleState.STARTED) {
            val rejected = BackendPublishResult.Rejected("backend not started")
            synchronized(lock) { lastResult = rejected }
            return rejected
        }

        val bridgeResult = bridge.submitInputReport(WindowsHidReportPacker.packInputReport(state))
        val status = bridge.readStatus()
        val result = when (bridgeResult) {
            WindowsDriverBridgeResult.Ok -> BackendPublishResult.Published
            is WindowsDriverBridgeResult.Error -> BackendPublishResult.Rejected(bridgeResult.detail)
        }
        synchronized(lock) {
            bridgeStatus = status
            if (result == BackendPublishResult.Published) {
                this.state = state
            }
            lastResult = result
        }
        return result
    }

    fun drainOutputHaptics(nowElapsedNanos: Long): List<HapticCommand> {
        require(nowElapsedNanos >= 0L) { "nowElapsedNanos must be non-negative" }
        if (lifecycleState != BackendLifecycleState.STARTED) return emptyList()

        val reports = mutableListOf<ByteArray>()
        while (reports.size < maxOutputReportsPerDrain) {
            reports += bridge.readOutputReport() ?: break
        }
        if (reports.isEmpty()) return emptyList()

        return synchronized(lock) {
            reports.mapNotNull { report ->
                outputReportCounter += 1L
                WindowsOutputReportMapper.toHapticCommand(
                    reportBytes = report,
                    commandId = "windows-output-report-$outputReportCounter",
                )
            }
        }
    }

    override fun simulateOutputReport(report: SimulatedOutputReport): HapticCommand? =
        synchronized(lock) {
            if (report.pattern != null) return@synchronized null
            simulatedOutputReportCounter += 1L
            WindowsOutputReportMapper.toHapticCommand(
                reportBytes = report.toWindowsOutputReportBytes(),
                commandId = "windows-output-report-simulated-$simulatedOutputReportCounter",
            )
        }

    override fun stop(reason: String): BackendLifecycleResult {
        synchronized(lock) {
            lifecycle = BackendLifecycleState.STOPPED
            bridgeStatus = null
        }
        bridge.close()
        return BackendLifecycleResult.Stopped(reason)
    }

    private fun SimulatedOutputReport.toWindowsOutputReportBytes(): ByteArray {
        val strengthByte = (strength * 255.0).toInt().coerceIn(0, 255)
        return byteArrayOf(
            WINDOWS_OUTPUT_REPORT_ID.toByte(),
            WINDOWS_OUTPUT_REPORT_VERSION.toByte(),
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
