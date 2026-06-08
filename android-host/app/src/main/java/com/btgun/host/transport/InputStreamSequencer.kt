package com.btgun.host.transport

class InputStreamSequencer {
    private var activeStreamSessionIdHex: String? = null
    private var nextSequence: Long = 1L

    fun resetFor(streamSessionIdHex: String) {
        require(streamSessionIdHex.length == 32 && streamSessionIdHex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "streamSessionIdHex must be 16 lowercase hex bytes"
        }
        activeStreamSessionIdHex = streamSessionIdHex
        nextSequence = 1L
    }

    fun next(): Long {
        require(activeStreamSessionIdHex != null) { "stream session must be initialized" }
        val sequence = nextSequence
        nextSequence += 1L
        return sequence
    }
}
