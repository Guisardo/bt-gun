package com.btgun.desktop.backend

data class UnsupportedReason(
    val platform: String,
    val feature: String,
    val detail: String,
) {
    init {
        require(platform.isNotBlank()) { "platform must be nonblank" }
        require(feature.isNotBlank()) { "feature must be nonblank" }
        require(detail.isNotBlank()) { "detail must be nonblank" }
    }
}

data class HapticEffectCapability(
    val strength: Boolean,
    val duration: Boolean,
    val pattern: Boolean,
    val phoneHaptic: Boolean,
    val outputReport: Boolean,
    val unsupported: List<UnsupportedReason>,
)

data class BackendCapabilities(
    val platform: String,
    val buttons: List<String>,
    val axes: List<String>,
    val haptics: HapticEffectCapability,
    val lifecycle: List<BackendLifecycleState>,
    val limitations: List<UnsupportedReason>,
) {
    init {
        require(platform.isNotBlank()) { "platform must be nonblank" }
        require(buttons.isNotEmpty()) { "buttons must not be empty" }
        require(axes.isNotEmpty()) { "axes must not be empty" }
    }
}

object BackendCapabilityPresets {
    fun macosStub(): BackendCapabilities =
        stub(
            platform = "macos-stub",
            deviceDetail = "Phase 5 macOS stub does not create OS-visible virtual controller devices.",
        )

    fun windowsStub(): BackendCapabilities =
        stub(
            platform = "windows-stub",
            deviceDetail = "Phase 5 Windows stub does not create OS-visible virtual controller devices.",
        )

    fun windowsVhf(): BackendCapabilities =
        BackendCapabilities(
            platform = "windows-vhf",
            buttons = btGunV1Descriptor.buttons,
            axes = btGunV1Descriptor.axes,
            haptics = HapticEffectCapability(
                strength = true,
                duration = true,
                pattern = false,
                phoneHaptic = true,
                outputReport = true,
                unsupported = listOf(
                    UnsupportedReason(
                        platform = "windows-vhf",
                        feature = "pattern",
                        detail = "Windows VHF output reports support phone haptic strength and duration only; pattern output is unsupported in v1.",
                    ),
                ),
            ),
            lifecycle = listOf(BackendLifecycleState.STOPPED, BackendLifecycleState.STARTED),
            limitations = emptyList(),
        )

    private fun stub(platform: String, deviceDetail: String): BackendCapabilities =
        BackendCapabilities(
            platform = platform,
            buttons = btGunV1Descriptor.buttons,
            axes = btGunV1Descriptor.axes,
            haptics = HapticEffectCapability(
                strength = true,
                duration = true,
                pattern = false,
                phoneHaptic = true,
                outputReport = false,
                unsupported = listOf(
                    UnsupportedReason(
                        platform = platform,
                        feature = "pattern",
                        detail = "Phase 5 phone haptic feedback supports strength and duration only.",
                    ),
                    UnsupportedReason(
                        platform = platform,
                        feature = "output-report",
                        detail = "Phase 5 stub records simulated output reports but exposes no OS output-report channel.",
                    ),
                ),
            ),
            lifecycle = listOf(BackendLifecycleState.STOPPED, BackendLifecycleState.STARTED),
            limitations = listOf(
                UnsupportedReason(
                    platform = platform,
                    feature = "os-visible-device",
                    detail = deviceDetail,
                ),
            ),
        )
}
