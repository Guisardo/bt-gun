package com.btgun.desktop.backend

data class VirtualControllerDescriptor(
    val deviceKind: String,
    val buttons: List<String>,
    val axes: List<String>,
    val triggerKind: String,
)

val btGunV1Descriptor = VirtualControllerDescriptor(
    deviceKind = "gamepad_like_joystick",
    buttons = listOf("trigger", "reload", "x", "y", "a", "b"),
    axes = listOf("stickX", "stickY", "aimX", "aimY"),
    triggerKind = "digital",
)

fun requireBtGunV1Invariant(descriptor: VirtualControllerDescriptor) {
    require(descriptor.deviceKind == btGunV1Descriptor.deviceKind) {
        "deviceKind must be ${btGunV1Descriptor.deviceKind}"
    }
    require(descriptor.buttons == btGunV1Descriptor.buttons) {
        "buttons must be ${btGunV1Descriptor.buttons.joinToString(",")}"
    }
    require(descriptor.axes == btGunV1Descriptor.axes) {
        "axes must be ${btGunV1Descriptor.axes.joinToString(",")}"
    }
    require(descriptor.triggerKind == btGunV1Descriptor.triggerKind) {
        "triggerKind must be ${btGunV1Descriptor.triggerKind}"
    }
}
