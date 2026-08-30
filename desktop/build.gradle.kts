import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("org.jetbrains.compose.hot-reload")
}

kotlin {
    jvmToolchain(17)
}

val generateBuildInfo = tasks.register("generateBuildInfo") {
    description = "Generates BuildInfo.kt with the version from the version catalog"
    group = "build"
    val version = libs.versions.appVersion.get()
    val outDir = layout.buildDirectory.dir("generated/buildinfo")
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/drdisagree/teledrive/desktop/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        val content = listOf(
            "package com.drdisagree.teledrive.desktop",
            "",
            "object BuildInfo {",
            "    const val VERSION = \"$version\"",
            "}",
            ""
        )
        file.writeText(content.joinToString(System.lineSeparator()))
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateBuildInfo)
}

compose.resources {
    packageOfResClass = "com.drdisagree.teledrive.desktop.resources"
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.components.resources)
    implementation(libs.cmp.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.jna.platform)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
}

compose.desktop {
    application {
        mainClass = "com.drdisagree.teledrive.desktop.MainKt"
        providers.gradleProperty("desktopJavaHome").orNull?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "TeleDrive"
            packageVersion = libs.versions.appVersion.get()
            description = "Private cloud storage on your own Telegram channel"
            modules(
                "java.instrument",
                "java.naming",
                "java.sql",
                "jdk.crypto.ec",
                "jdk.httpserver",
                "jdk.unsupported"
            )
            vendor = "DrDisagree"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                iconFile.set(project.file("icons/TeleDrive.ico"))
                menuGroup = "TeleDrive"
                shortcut = true
                dirChooser = true
                upgradeUuid = "b7e35f74-33a4-43d9-98b1-84babb95f8a7"
            }

            linux {
                iconFile.set(project.file("icons/TeleDrive.png"))
            }

            macOS {
                iconFile.set(project.file("icons/TeleDrive.icns"))
                bundleID = "com.drdisagree.teledrive"
            }
        }
    }
}
