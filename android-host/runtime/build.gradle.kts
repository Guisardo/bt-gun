import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.btgun.host.runtime"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.withType<Test>().configureEach {
    val unitTestTask = this

    filter {
        // Main-function tests run below; keep Gradle's JUnit scanner no-op on Gradle 8/9.
        includeTestsMatching("com.btgun.no_junit_tests.*")
        isFailOnNoMatchingTests = false
    }

    doLast {
        listOf(
            "com.btgun.host.permissions.PermissionGateTest",
            "com.btgun.host.permissions.AndroidHidCapabilityTestKt",
            "com.btgun.host.hid.BtGunHidDescriptorTestKt",
            "com.btgun.host.hid.BtGunHidReportPackerTestKt",
            "com.btgun.host.hid.BtGunHidOutputReportMapperTestKt",
            "com.btgun.host.hid.AndroidBluetoothHidGamepadStateTestKt",
            "com.btgun.host.ble.IpegaPacketParserTestKt",
            "com.btgun.host.ble.IpegaBleGunAdapterTestKt",
            "com.btgun.host.model.NormalizedEventEnvelopeTestKt",
            "com.btgun.host.motion.MotionProviderSelectionTestKt",
            "com.btgun.host.motion.AimCalibrationTestKt",
            "com.btgun.host.recenter.ReloadHoldRecenterTestKt",
            "com.btgun.host.session.PairingPayloadTestKt",
            "com.btgun.host.session.TrustedDesktopStoreTestKt",
            "com.btgun.host.session.DesktopControlClientTestKt",
            "com.btgun.host.session.VisualizerStatusTestKt",
            "com.btgun.host.diagnostics.DiagnosticEventTestKt",
            "com.btgun.host.diagnostics.DiagnosticReporterTestKt",
            "com.btgun.host.HostSessionServiceLivenessTestKt",
            "com.btgun.host.camera.CameraZoomModelTestKt",
            "com.btgun.host.play.PlayModeControllerTestKt",
            "com.btgun.host.haptics.DesktopHapticCommandTestKt",
            "com.btgun.host.transport.UdpInputFrameCodecTestKt",
            "com.btgun.host.transport.AndroidUdpInputSenderTestKt",
            "com.btgun.host.transport.InputStreamLifecycleTestKt",
            "com.btgun.host.ui.DashboardStateTestKt",
            "com.btgun.host.profile.AdaptiveAimSmootherTestKt",
            "com.btgun.host.profile.ProfileMapperTestKt",
            "com.btgun.host.profile.ProfileStoreTestKt",
            "com.btgun.host.profile.ProfileValidationTestKt",
            "com.btgun.host.planning.Phase11DocsGuardTestKt",
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
