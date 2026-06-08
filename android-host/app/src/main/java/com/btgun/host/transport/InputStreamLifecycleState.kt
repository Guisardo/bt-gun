package com.btgun.host.transport

enum class InputStreamLifecycleState(val label: String) {
    ACTIVE("active"),
    GRACE("grace"),
    STALE("stale"),
    STOPPED("stopped"),
}
