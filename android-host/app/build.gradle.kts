import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.btgun.host"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.btgun.host"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase2-host"
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
    failOnNoDiscoveredTests = false

    filter {
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
            "com.btgun.host.HostSessionServiceLivenessTestKt",
            "com.btgun.host.haptics.DesktopHapticCommandTestKt",
            "com.btgun.host.transport.UdpInputFrameCodecTestKt",
            "com.btgun.host.transport.AndroidUdpInputSenderTestKt",
            "com.btgun.host.transport.InputStreamLifecycleTestKt",
            "com.btgun.host.ui.DashboardStateTestKt",
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
