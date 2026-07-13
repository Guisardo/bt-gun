package com.btgun.host.planning

import java.io.File

fun main() {
    phase11DocsExistAndNameUserAppContracts()
    userAppAutoStartsGunConnection()
    userAppStagesHudAndProfileReloads()
}

private fun phase11DocsExistAndNameUserAppContracts() {
    val root = findRepoRoot()
    val context = root.resolve(".planning/phases/11-gamepad-extension-android-user-app/11-CONTEXT.md")
    val plan = root.resolve(".planning/phases/11-gamepad-extension-android-user-app/11-01-PLAN.md")
    val state = root.resolve(".planning/STATE.md")
    val roadmap = root.resolve(".planning/ROADMAP.md")

    listOf(context, plan).forEach { file ->
        if (!file.isFile) {
            throw AssertionError("missing ${file.path}")
        }
    }
    val contextText = context.readText()
    val planText = plan.readText()
    val stateText = state.readText()
    val roadmapText = roadmap.readText()

    expectContains("context app name", contextText, "Gamepad Extension")
    expectContains("figma style", contextText, "#00ff41")
    expectContains("play mode", contextText, "PlayModeController")
    expectContains("zoom rail", contextText, "right-side zoom rail")
    expectContains("compact v2", contextText, "compact LAN v2")
    expectContains("plan package", planText, "com.btgun.gamepadextension")
    expectContains("state active", stateText, "Phase 11")
    expectContains("roadmap active", roadmapText, "Gamepad Extension Android User App")
}

private fun userAppAutoStartsGunConnection() {
    val root = findRepoRoot()
    val activity = root.resolve("android-host/user-app/src/main/java/com/btgun/gamepadextension/GamepadExtensionActivity.kt")
    if (!activity.isFile) {
        throw AssertionError("missing ${activity.path}")
    }
    val activityText = activity.readText()
    expectContains("auto gun start helper", activityText, "startGunConnectionCycle")
    expectContains("gun gate auto post", activityText, "handler.post { startGunConnectionCycle() }")
    expectNotContains("manual connect action", activityText, "action(\"Connect gun\"")
}

private fun userAppStagesHudAndProfileReloads() {
    val root = findRepoRoot()
    val activity = root.resolve("android-host/user-app/src/main/java/com/btgun/gamepadextension/GamepadExtensionActivity.kt")
    val service = root.resolve("android-host/runtime/src/main/java/com/btgun/host/HostSessionService.kt")
    val playMode = root.resolve("android-host/runtime/src/main/java/com/btgun/host/play/PlayModeController.kt")
    val activityText = activity.readText()
    val serviceText = service.readText()
    val playModeText = playMode.readText()

    expectContains("bluetooth opens pairing window", activityText, "ACTION_START_HID_PAIRING_WINDOW")
    expectContains("profile reload helper", activityText, "reloadActiveProfileIfRuntimeActive")
    expectContains("profile reload guarded", activityText, "shouldStartServiceForProfileReload")
    expectContains("camera generation guard", activityText, "cameraGeneration")
    expectContains("first frame preview gate", activityText, "onSurfaceTextureUpdated")
    expectContains("user app closes output gate", activityText, "ACTION_CLOSE_OUTPUT_SEND_GATE")
    expectContains("user app opens output gate", activityText, "ACTION_OPEN_OUTPUT_SEND_GATE")
    expectContains("service output close action", serviceText, "ACTION_CLOSE_OUTPUT_SEND_GATE")
    expectContains("service output open action", serviceText, "ACTION_OPEN_OUTPUT_SEND_GATE")
    expectContains("play mode output gate", playModeText, "outputGateOpen")
}

private fun findRepoRoot(): File {
    var current: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
    repeat(8) {
        val candidate = current ?: return@repeat
        if (candidate.resolve(".planning").isDirectory) {
            return candidate
        }
        current = candidate.parentFile
    }
    throw AssertionError("repo root not found from ${System.getProperty("user.dir")}")
}

private fun expectContains(label: String, text: String, needle: String) {
    if (!text.contains(needle)) {
        throw AssertionError("$label expected <$needle>")
    }
}

private fun expectNotContains(label: String, text: String, needle: String) {
    if (text.contains(needle)) {
        throw AssertionError("$label should not contain <$needle>")
    }
}
