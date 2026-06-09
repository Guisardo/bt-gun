package com.btgun.desktop.backend

fun main() {
    macosAndWindowsStubCapabilitiesMatchDescriptor()
    stubCapabilitiesDeclarePhaseFiveHapticMatrix()
    unsupportedReasonsAreStructured()
    stubBackendLifecyclePublishAndStopAreObservable()
}

private fun macosAndWindowsStubCapabilitiesMatchDescriptor() {
    stubCapabilities().forEach { capabilities ->
        expectEquals("${capabilities.platform} buttons", btGunV1Descriptor.buttons, capabilities.buttons)
        expectEquals("${capabilities.platform} axes", btGunV1Descriptor.axes, capabilities.axes)
        expectEquals("${capabilities.platform} button count", 6, capabilities.buttons.size)
        expectEquals("${capabilities.platform} axis count", 4, capabilities.axes.size)
        expectEquals("${capabilities.platform} trigger", "digital", btGunV1Descriptor.triggerKind)
    }
}

private fun stubCapabilitiesDeclarePhaseFiveHapticMatrix() {
    stubCapabilities().forEach { capabilities ->
        expectEquals("${capabilities.platform} haptic strength", true, capabilities.haptics.strength)
        expectEquals("${capabilities.platform} haptic duration", true, capabilities.haptics.duration)
        expectEquals("${capabilities.platform} haptic pattern", false, capabilities.haptics.pattern)
        expectEquals("${capabilities.platform} phone haptic", true, capabilities.haptics.phoneHaptic)
        expectEquals("${capabilities.platform} output report", false, capabilities.haptics.outputReport)
    }
}

private fun unsupportedReasonsAreStructured() {
    stubCapabilities().forEach { capabilities ->
        val reasons = capabilities.limitations + capabilities.haptics.unsupported
        expectTrue("${capabilities.platform} has limitations", reasons.isNotEmpty())
        expectTrue(
            "${capabilities.platform} explains OS-visible device",
            reasons.any { it.feature == "os-visible-device" },
        )
        expectTrue(
            "${capabilities.platform} explains output report",
            reasons.any { it.feature == "output-report" },
        )
        reasons.forEach { reason ->
            expectTrue("reason platform nonblank", reason.platform.isNotBlank())
            expectTrue("reason feature nonblank", reason.feature.isNotBlank())
            expectTrue("reason detail nonblank", reason.detail.isNotBlank())
        }
    }
}

private fun stubBackendLifecyclePublishAndStopAreObservable() {
    val backend: VirtualControllerBackend = StubVirtualControllerBackend.macos()
    val published = SemanticControllerState(
        trigger = true,
        reload = true,
        x = true,
        stickX = 123,
        stickY = -456,
        aimX = 0.25f,
        aimY = -0.5f,
        sourceSequence = 42L,
    )

    expectEquals("initial lifecycle", BackendLifecycleState.STOPPED, backend.lifecycleState)
    expectEquals("initial state", SemanticControllerState(), backend.currentState)
    expectEquals("start result", BackendLifecycleResult.Started, backend.start())
    expectEquals("started lifecycle", BackendLifecycleState.STARTED, backend.lifecycleState)
    expectEquals("publish result", BackendPublishResult.Published, backend.publish(published))
    expectEquals("current state", published, backend.currentState)
    expectEquals("last publish result", BackendPublishResult.Published, backend.lastPublishResult)
    expectEquals("stop result", BackendLifecycleResult.Stopped("done"), backend.stop("done"))
    expectEquals("stopped lifecycle", BackendLifecycleState.STOPPED, backend.lifecycleState)
}

private fun stubCapabilities(): List<BackendCapabilities> =
    listOf(
        BackendCapabilityPresets.macosStub(),
        BackendCapabilityPresets.windowsStub(),
    )

private fun expectEquals(label: String, expected: Any?, actual: Any?) {
    if (expected != actual) {
        throw AssertionError("$label expected <$expected> but was <$actual>")
    }
}

private fun expectTrue(label: String, actual: Boolean) {
    if (!actual) {
        throw AssertionError("$label expected true")
    }
}
