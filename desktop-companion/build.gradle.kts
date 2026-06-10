import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.0.21"
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

application {
    mainClass.set("com.btgun.desktop.MainKt")
}

val btgunSmokeHapticEnabled = providers.gradleProperty("btgun.smoke.haptic")
    .map { value -> value.equals("true", ignoreCase = true) }
    .orElse(false)

val btgunWindowsDriverBridgePath = providers.systemProperty("btgun.windows.driver.bridge.path")
    .orElse("")

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.0")
    implementation("io.ktor:ktor-server-websockets-jvm:3.5.0")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")
}

tasks.withType<Test>().configureEach {
    val unitTestTask = this
    failOnNoDiscoveredTests = false

    filter {
        isFailOnNoMatchingTests = false
    }

    doLast {
        listOf(
            "com.btgun.desktop.pairing.PairingSessionRegistryTestKt",
            "com.btgun.desktop.security.PairingSecurityTestKt",
            "com.btgun.desktop.control.ControlChannelTestKt",
            "com.btgun.desktop.transport.UdpInputFrameCodecTestKt",
            "com.btgun.desktop.transport.InputReplayGuardTestKt",
            "com.btgun.desktop.transport.UdpInputReceiverTestKt",
            "com.btgun.desktop.transport.DesktopUdpInputRuntimeTestKt",
            "com.btgun.desktop.transport.InputStreamLifecycleTestKt",
            "com.btgun.desktop.haptics.HapticCommandCodecTestKt",
            "com.btgun.desktop.ui.PairingWindowTestKt",
            "com.btgun.desktop.backend.BackendContractTestKt",
            "com.btgun.desktop.backend.BackendCapabilitiesTestKt",
            "com.btgun.desktop.backend.UdpControllerStateAdapterTestKt",
            "com.btgun.desktop.backend.BackendHapticSmokeTestKt",
            "com.btgun.desktop.backend.windows.WindowsHidReportPackerTestKt",
            "com.btgun.desktop.backend.windows.WindowsOutputReportMapperTestKt",
            "com.btgun.desktop.backend.windows.WindowsVirtualControllerBackendTestKt",
            "com.btgun.desktop.backend.windows.WindowsBackendRuntimeTestKt",
            "com.btgun.desktop.backend.macos.MacosHidReportPackerTestKt",
            "com.btgun.desktop.backend.macos.MacosOutputReportMapperTestKt",
            "com.btgun.desktop.backend.macos.MacosVirtualControllerBackendTestKt",
            "com.btgun.desktop.backend.macos.MacosBackendRuntimeTestKt",
            "com.btgun.desktop.smoke.BackendSmokeRunnerTestKt",
        ).forEach { testClass ->
            providers.exec {
                commandLine(
                    "java",
                    "-cp",
                    project.files(unitTestTask.testClassesDirs, unitTestTask.classpath).asPath,
                    testClass,
                )
            }.result.get().assertNormalExitValue()
        }
    }
}

tasks.register<JavaExec>("smokeDesktopBackendMacosStub") {
    group = "verification"
    description = "Runs the macOS desktop backend stub smoke and writes JUnit-style XML."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.btgun.desktop.smoke.MacosBackendSmokeMainKt")
    systemProperty("btgun.smoke.haptic", btgunSmokeHapticEnabled.get().toString())
}

tasks.register<JavaExec>("smokeDesktopBackendWindowsStub") {
    group = "verification"
    description = "Runs the Windows desktop backend stub smoke and writes JUnit-style XML."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.btgun.desktop.smoke.WindowsBackendSmokeMainKt")
    systemProperty("btgun.smoke.haptic", btgunSmokeHapticEnabled.get().toString())
}

tasks.register<JavaExec>("smokeDesktopBackendWindowsVhf") {
    group = "verification"
    description = "Runs the Windows VHF desktop backend smoke against a Plan 05 driver bridge artifact."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.btgun.desktop.smoke.WindowsVhfBackendSmokeMainKt")
    systemProperty("btgun.windows.driver.bridge.path", btgunWindowsDriverBridgePath.get())
}
