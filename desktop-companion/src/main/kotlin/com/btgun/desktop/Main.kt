package com.btgun.desktop

import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.backend.macos.MacosBackendRuntime
import com.btgun.desktop.backend.macos.MacosBackendRuntimeConfig
import com.btgun.desktop.backend.windows.WindowsBackendRuntime
import com.btgun.desktop.backend.windows.WindowsBackendRuntimeConfig
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.ui.DesktopUiEventHub
import com.btgun.desktop.ui.DesktopUiEventListener
import com.btgun.desktop.ui.PairingWindow
import com.btgun.desktop.ui.VisualizerWindowCoordinator
import com.btgun.desktop.ui.VisualizerWindowFactory
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        createPairingWindow().show()
    }
}

private fun createPairingWindow(): PairingWindow {
    val identityStore = DesktopIdentityStore.default()
    val registry = PairingSessionRegistry(identityStore = identityStore)
    val controlServer = ControlServer(registry = registry)
    val eventHub = DesktopUiEventHub(controlServer).attach()
    val visualizerFactory = VisualizerWindowFactory {
        com.btgun.desktop.ui.VisualizerWindow(controlServer = controlServer)
    }
    val coordinator = VisualizerWindowCoordinator(visualizerFactory)
    eventHub.listen(
        DesktopUiEventListener(
            onSessionStateChanged = coordinator::onSessionStateChanged,
            onHapticResultReceived = coordinator::onHapticResultReceived,
        ),
    )
    val windowsBackendLaunch = createWindowsBackendLaunch()
    val macosBackendLaunch = createMacosBackendLaunch()
    return PairingWindow(
        registry = registry,
        controlServer = controlServer,
        windowsBackendRuntime = windowsBackendLaunch.runtime,
        windowsBackendStartupDiagnostic = windowsBackendLaunch.diagnostic,
        macosBackendRuntime = macosBackendLaunch.runtime,
        macosBackendStartupDiagnostic = macosBackendLaunch.diagnostic,
        openVisualizer = coordinator::openVisualizer,
        eventHub = eventHub,
    )
}

private fun createWindowsBackendLaunch(): WindowsBackendLaunch {
    val enabled = System.getProperty(WINDOWS_DRIVER_ENABLED_PROPERTY).equals("true", ignoreCase = true)
    if (!enabled) {
        return WindowsBackendLaunch(runtime = null, diagnostic = "disabled")
    }
    val bridgePath = System.getProperty(WINDOWS_DRIVER_BRIDGE_PATH_PROPERTY)?.trim().orEmpty()
    if (bridgePath.isBlank()) {
        return WindowsBackendLaunch(
            runtime = null,
            diagnostic = "enabled but btgun.windows.driver.bridge.path is missing",
        )
    }
    return WindowsBackendLaunch(
        runtime = WindowsBackendRuntime(WindowsBackendRuntimeConfig(bridgePath = bridgePath)),
        diagnostic = "enabled",
    )
}

private fun createMacosBackendLaunch(): MacosBackendLaunch {
    val enabled = System.getProperty(MACOS_HID_ENABLED_PROPERTY).equals("true", ignoreCase = true)
    if (!enabled) {
        return MacosBackendLaunch(runtime = null, diagnostic = "disabled")
    }
    val helperPath = System.getProperty(MACOS_HID_HELPER_PATH_PROPERTY)?.trim().orEmpty()
    if (helperPath.isBlank()) {
        return MacosBackendLaunch(
            runtime = null,
            diagnostic = "enabled but btgun.macos.hid.helper.path is missing",
        )
    }
    return MacosBackendLaunch(
        runtime = MacosBackendRuntime(MacosBackendRuntimeConfig(helperPath = helperPath)),
        diagnostic = "enabled",
    )
}

private data class WindowsBackendLaunch(
    val runtime: WindowsBackendRuntime?,
    val diagnostic: String,
)

private data class MacosBackendLaunch(
    val runtime: MacosBackendRuntime?,
    val diagnostic: String,
)

private const val WINDOWS_DRIVER_ENABLED_PROPERTY = "btgun.windows.driver.enabled"
private const val WINDOWS_DRIVER_BRIDGE_PATH_PROPERTY = "btgun.windows.driver.bridge.path"
private const val MACOS_HID_ENABLED_PROPERTY = "btgun.macos.hid.enabled"
private const val MACOS_HID_HELPER_PATH_PROPERTY = "btgun.macos.hid.helper.path"
