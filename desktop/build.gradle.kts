import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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
    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.components.resources)
    implementation(libs.cmp.material3)
    implementation(libs.kotlinx.coroutines.swing)
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

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "TeleDrive"
            packageVersion = libs.versions.appVersion.get()
        }
    }
}
