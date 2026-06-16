package com.btgun.host.ble

class GattOperationQueue(
    private val listener: (GattOperationEvent) -> Unit = {},
) {
    private val pending = ArrayDeque<PendingGattOperation>()
    private var inFlight = false

    var inFlightOperationName: String? = null
        private set

    fun enqueue(name: String, start: () -> Boolean) {
        pending.addLast(PendingGattOperation(name, start))
        drain()
    }

    fun completeDescriptorWrite(status: Int) {
        complete("descriptor_write", status)
    }

    fun completeCharacteristicRead(status: Int) {
        complete("characteristic_read", status)
    }

    fun completeCharacteristicWrite(status: Int) {
        complete("characteristic_write", status)
    }

    fun completeDescriptorRead(status: Int) {
        complete("descriptor_read", status)
    }

    fun clear() {
        pending.clear()
        inFlight = false
        inFlightOperationName = null
    }

    private fun drain() {
        if (inFlight || pending.isEmpty()) {
            return
        }

        val operation = pending.removeFirst()
        inFlight = true
        inFlightOperationName = operation.name
        val started = operation.start()
        listener(GattOperationEvent(state = if (started) "started" else "start_failed", operationName = operation.name))
        if (!started) {
            inFlight = false
            inFlightOperationName = null
            drain()
        }
    }

    private fun complete(callbackName: String, status: Int) {
        inFlight = false
        inFlightOperationName = null
        listener(GattOperationEvent(state = "completed", callbackName = callbackName, status = status))
        drain()
    }

    private data class PendingGattOperation(
        val name: String,
        val start: () -> Boolean,
    )
}

data class GattOperationEvent(
    val state: String,
    val operationName: String? = null,
    val callbackName: String? = null,
    val status: Int? = null,
)
