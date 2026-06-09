package com.btgun.desktop.backend

data class SimulatedOutputReport(
    val strength: Double,
    val durationMs: Long,
    val ttlMs: Long,
    val pattern: String? = null,
) {
    init {
        require(strength in 0.0..1.0) { "strength must be in 0.0..1.0" }
        require(durationMs in 1L..1_000L) { "durationMs must be in 1..1000" }
        require(ttlMs in 1L..2_000L) { "ttlMs must be in 1..2000" }
    }
}
