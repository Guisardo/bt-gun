package com.btgun.desktop.ui

import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JScrollPane

internal object DesktopWindowFit {
    private const val SCREEN_MARGIN_PX = 48

    fun scrollableContent(content: JComponent): JScrollPane =
        JScrollPane(content).apply {
            border = null
            viewport.background = content.background
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

    fun fitToScreen(frame: JFrame) {
        val usableBounds = usableScreenBounds(frame)
        val capped = constrainedFrameSize(frame.size, usableBounds.size)
        if (capped != frame.size) {
            frame.size = capped
        }
    }

    internal fun constrainedFrameSize(packedSize: Dimension, usableScreenSize: Dimension): Dimension {
        val maxWidth = (usableScreenSize.width - SCREEN_MARGIN_PX).coerceAtLeast(1)
        val maxHeight = (usableScreenSize.height - SCREEN_MARGIN_PX).coerceAtLeast(1)
        return Dimension(
            packedSize.width.coerceAtMost(maxWidth),
            packedSize.height.coerceAtMost(maxHeight),
        )
    }

    private fun usableScreenBounds(window: Window): Rectangle {
        val configuration = window.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val bounds = Rectangle(configuration.bounds)
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        bounds.x += insets.left
        bounds.y += insets.top
        bounds.width -= insets.left + insets.right
        bounds.height -= insets.top + insets.bottom
        return bounds
    }
}
