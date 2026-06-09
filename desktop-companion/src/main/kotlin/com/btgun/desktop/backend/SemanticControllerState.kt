package com.btgun.desktop.backend

data class SemanticControllerState(
    val trigger: Boolean = false,
    val reload: Boolean = false,
    val x: Boolean = false,
    val y: Boolean = false,
    val a: Boolean = false,
    val b: Boolean = false,
    val stickX: Int = 0,
    val stickY: Int = 0,
    val aimX: Float = 0.0f,
    val aimY: Float = 0.0f,
    val stale: Boolean = false,
    val sourceSequence: Long? = null,
)
