plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.drdisagree.teledrive.ui"
        compileSdk = 37
        minSdk = 26
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                api(compose.runtime)
                api(libs.cmp.components.resources)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.drdisagree.teledrive.resources"
    publicResClass = true
}
