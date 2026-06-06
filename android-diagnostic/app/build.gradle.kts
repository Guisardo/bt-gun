plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.btgun.diagnostic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.btgun.diagnostic"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1-diagnostic"
    }
}
