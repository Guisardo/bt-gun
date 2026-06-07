package com.btgun.desktop

import com.btgun.desktop.ui.PairingWindow
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        PairingWindow().show()
    }
}
