plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.drdisagree.teledrive.shared"
        compileSdk = 37
        minSdk = 26
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(libs.androidx.paging.common)
                api(libs.androidx.room.runtime)
                api(libs.androidx.room.paging)
                api(libs.androidx.sqlite.bundled)
                api(libs.androidx.datastore.preferences.core)
            }
        }
        val jvmCommonMain = create("jvmCommonMain") {
            dependsOn(commonMain.get())
        }
        val jvmCommonTest = create("jvmCommonTest") {
            dependsOn(commonTest.get())
        }
        named("androidMain") {
            dependsOn(jvmCommonMain)
        }
        named("desktopMain") {
            dependsOn(jvmCommonMain)
        }
        named("desktopTest") {
            dependsOn(jvmCommonTest)
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}
