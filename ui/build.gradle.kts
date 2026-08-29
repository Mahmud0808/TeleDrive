plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
        val jvmCommonMain = create("jvmCommonMain") {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.zip4j)
            }
        }
        named("androidMain") {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.google.android.material)
            }
        }
        named("desktopMain") {
            dependsOn(jvmCommonMain)
        }
        commonMain {
            dependencies {
                api(project(":shared"))
                api(libs.cmp.runtime)
                api(libs.cmp.foundation)
                api(libs.cmp.ui)
                api(libs.cmp.ui.backhandler)
                api(libs.cmp.components.resources)
                api(libs.cmp.material3)
                api(libs.cmp.material.icons.extended)
                api(libs.jb.navigation.compose)
                api(libs.jb.material3.adaptive)
                api(libs.jb.lifecycle.viewmodel.compose)
                api(libs.jb.lifecycle.runtime.compose)
                implementation(project.dependencies.platform(libs.koin.bom))
                api(libs.koin.compose)
                api(libs.koin.compose.viewmodel)
                api(libs.androidx.paging.compose)
                api(libs.coil.compose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.zxing.core)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.drdisagree.teledrive.resources"
    publicResClass = true
}
