plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.drdisagree.teledrive.shared"
        compileSdk = 37
        minSdk = 26
    }

    jvm("desktop")

    sourceSets {
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(libs.androidx.paging.common)
            }
        }
        val jvmCommonTest by creating {
            dependsOn(commonTest.get())
        }
        getByName("androidMain").dependsOn(jvmCommonMain)
        getByName("desktopMain").dependsOn(jvmCommonMain)
        getByName("desktopTest") {
            dependsOn(jvmCommonTest)
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}
