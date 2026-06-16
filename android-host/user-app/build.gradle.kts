plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.btgun.gamepadextension"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.btgun.gamepadextension"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-v1.1-user"
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
    implementation(project(":runtime"))
}
