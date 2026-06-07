package com.btgun.desktop

import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.pairing.PairingSessionRegistry
import com.btgun.desktop.security.DesktopIdentityStore
import com.btgun.desktop.ui.PairingWindow
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
    return PairingWindow(
        registry = registry,
        controlServer = controlServer,
    )
}
