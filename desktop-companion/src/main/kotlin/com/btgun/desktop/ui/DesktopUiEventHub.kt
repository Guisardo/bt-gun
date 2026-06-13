package com.btgun.desktop.ui

import com.btgun.desktop.control.ControlEnvelope
import com.btgun.desktop.control.ControlServer
import com.btgun.desktop.control.ControlServerSessionState
import com.btgun.desktop.control.ProfileMetadata
import com.btgun.desktop.haptics.HapticResult
import com.btgun.desktop.transport.InputReplayRejectReason
import com.btgun.desktop.transport.InputStreamLifecycleState
import com.btgun.desktop.transport.UdpReceivedInput

data class DesktopUiEventListener(
    val onSessionStateChanged: (ControlServerSessionState) -> Unit = {},
    val onControlEnvelopeAccepted: (ControlEnvelope) -> Unit = {},
    val onProfileMetadataReceived: (ProfileMetadata) -> Unit = {},
    val onUdpInputReceived: (UdpReceivedInput) -> Unit = {},
    val onUdpInputRejected: (InputReplayRejectReason) -> Unit = {},
    val onUdpInputStateChanged: (InputStreamLifecycleState) -> Unit = {},
    val onHapticResultReceived: (HapticResult) -> Unit = {},
)

class DesktopUiEventHub(
    private val controlServer: ControlServer,
) : AutoCloseable {
    private val lock = Any()
    private val listeners = mutableListOf<DesktopUiEventListener>()
    private var attached = false

    private var previousSessionStateChanged: ((ControlServerSessionState) -> Unit)? = null
    private var previousControlEnvelopeAccepted: ((ControlEnvelope) -> Unit)? = null
    private var previousProfileMetadataReceived: ((ProfileMetadata) -> Unit)? = null
    private var previousUdpInputReceived: ((UdpReceivedInput) -> Unit)? = null
    private var previousUdpInputRejected: ((InputReplayRejectReason) -> Unit)? = null
    private var previousUdpInputStateChanged: ((InputStreamLifecycleState) -> Unit)? = null
    private var previousHapticResultReceived: ((HapticResult) -> Unit)? = null

    private val sessionStateCallback: (ControlServerSessionState) -> Unit = { state ->
        previousSessionStateChanged?.invoke(state)
        listenerSnapshot().forEach { it.onSessionStateChanged(state) }
    }
    private val controlEnvelopeCallback: (ControlEnvelope) -> Unit = { envelope ->
        previousControlEnvelopeAccepted?.invoke(envelope)
        listenerSnapshot().forEach { it.onControlEnvelopeAccepted(envelope) }
    }
    private val profileMetadataCallback: (ProfileMetadata) -> Unit = { metadata ->
        previousProfileMetadataReceived?.invoke(metadata)
        listenerSnapshot().forEach { it.onProfileMetadataReceived(metadata) }
    }
    private val udpInputCallback: (UdpReceivedInput) -> Unit = { input ->
        previousUdpInputReceived?.invoke(input)
        listenerSnapshot().forEach { it.onUdpInputReceived(input) }
    }
    private val udpRejectedCallback: (InputReplayRejectReason) -> Unit = { reason ->
        previousUdpInputRejected?.invoke(reason)
        listenerSnapshot().forEach { it.onUdpInputRejected(reason) }
    }
    private val udpStateCallback: (InputStreamLifecycleState) -> Unit = { state ->
        previousUdpInputStateChanged?.invoke(state)
        listenerSnapshot().forEach { it.onUdpInputStateChanged(state) }
    }
    private val hapticResultCallback: (HapticResult) -> Unit = { result ->
        previousHapticResultReceived?.invoke(result)
        listenerSnapshot().forEach { it.onHapticResultReceived(result) }
    }

    fun attach(): DesktopUiEventHub {
        synchronized(lock) {
            require(!attached) { "DesktopUiEventHub is already attached" }
            previousSessionStateChanged = controlServer.onSessionStateChanged
            previousControlEnvelopeAccepted = controlServer.onControlEnvelopeAccepted
            previousProfileMetadataReceived = controlServer.onProfileMetadataReceived
            previousUdpInputReceived = controlServer.onUdpInputReceived
            previousUdpInputRejected = controlServer.onUdpInputRejected
            previousUdpInputStateChanged = controlServer.onUdpInputStateChanged
            previousHapticResultReceived = controlServer.onHapticResultReceived

            controlServer.onSessionStateChanged = sessionStateCallback
            controlServer.onControlEnvelopeAccepted = controlEnvelopeCallback
            controlServer.onProfileMetadataReceived = profileMetadataCallback
            controlServer.onUdpInputReceived = udpInputCallback
            controlServer.onUdpInputRejected = udpRejectedCallback
            controlServer.onUdpInputStateChanged = udpStateCallback
            controlServer.onHapticResultReceived = hapticResultCallback
            attached = true
        }
        return this
    }

    fun listen(listener: DesktopUiEventListener): AutoCloseable {
        synchronized(lock) {
            listeners.add(listener)
        }
        return AutoCloseable {
            synchronized(lock) {
                listeners.remove(listener)
            }
        }
    }

    override fun close() {
        val restore = synchronized(lock) {
            if (!attached) {
                null
            } else {
                attached = false
                listeners.clear()
                RestoreCallbacks(
                    onSessionStateChanged = previousSessionStateChanged,
                    onControlEnvelopeAccepted = previousControlEnvelopeAccepted,
                    onProfileMetadataReceived = previousProfileMetadataReceived,
                    onUdpInputReceived = previousUdpInputReceived,
                    onUdpInputRejected = previousUdpInputRejected,
                    onUdpInputStateChanged = previousUdpInputStateChanged,
                    onHapticResultReceived = previousHapticResultReceived,
                ).also {
                    previousSessionStateChanged = null
                    previousControlEnvelopeAccepted = null
                    previousProfileMetadataReceived = null
                    previousUdpInputReceived = null
                    previousUdpInputRejected = null
                    previousUdpInputStateChanged = null
                    previousHapticResultReceived = null
                }
            }
        } ?: return

        if (controlServer.onSessionStateChanged === sessionStateCallback) {
            controlServer.onSessionStateChanged = restore.onSessionStateChanged ?: {}
        }
        if (controlServer.onControlEnvelopeAccepted === controlEnvelopeCallback) {
            controlServer.onControlEnvelopeAccepted = restore.onControlEnvelopeAccepted ?: {}
        }
        if (controlServer.onProfileMetadataReceived === profileMetadataCallback) {
            controlServer.onProfileMetadataReceived = restore.onProfileMetadataReceived ?: {}
        }
        if (controlServer.onUdpInputReceived === udpInputCallback) {
            controlServer.onUdpInputReceived = restore.onUdpInputReceived ?: {}
        }
        if (controlServer.onUdpInputRejected === udpRejectedCallback) {
            controlServer.onUdpInputRejected = restore.onUdpInputRejected ?: {}
        }
        if (controlServer.onUdpInputStateChanged === udpStateCallback) {
            controlServer.onUdpInputStateChanged = restore.onUdpInputStateChanged ?: {}
        }
        if (controlServer.onHapticResultReceived === hapticResultCallback) {
            controlServer.onHapticResultReceived = restore.onHapticResultReceived ?: {}
        }
    }

    private fun listenerSnapshot(): List<DesktopUiEventListener> =
        synchronized(lock) { listeners.toList() }
}

private data class RestoreCallbacks(
    val onSessionStateChanged: ((ControlServerSessionState) -> Unit)?,
    val onControlEnvelopeAccepted: ((ControlEnvelope) -> Unit)?,
    val onProfileMetadataReceived: ((ProfileMetadata) -> Unit)?,
    val onUdpInputReceived: ((UdpReceivedInput) -> Unit)?,
    val onUdpInputRejected: ((InputReplayRejectReason) -> Unit)?,
    val onUdpInputStateChanged: ((InputStreamLifecycleState) -> Unit)?,
    val onHapticResultReceived: ((HapticResult) -> Unit)?,
)
