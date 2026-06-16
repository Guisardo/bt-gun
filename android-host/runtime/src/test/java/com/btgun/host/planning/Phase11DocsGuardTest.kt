package com.btgun.host.planning

import java.io.File

fun main() {
    phase11DocsExistAndNameUserAppContracts()
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
