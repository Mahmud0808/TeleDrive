import java.net.URI
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
    implementation(libs.vlcj)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
}

val vlcVersion = "3.0.21"
val vlcZip = layout.buildDirectory.file("vlc/vlc-$vlcVersion-win64.zip")

val downloadVlc = tasks.register("downloadVlc") {
    description = "Downloads the VLC natives archive for bundling"
    group = "build"
    val zipFile = vlcZip
    val archiveUrl = "https://download.videolan.org/pub/videolan/vlc/" +
            "$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
    outputs.file(zipFile)
    doLast {
        val target = zipFile.get().asFile
        if (target.length() > 0) return@doLast
        target.parentFile.mkdirs()
        URI(archiveUrl).toURL().openStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

val prepareVlcNatives = tasks.register<Copy>("prepareVlcNatives") {
    description = "Unpacks the VLC libraries the inline player loads"
    group = "build"
    dependsOn(downloadVlc)
    from(zipTree(vlcZip)) {
        include("vlc-$vlcVersion/libvlc.dll")
        include("vlc-$vlcVersion/libvlccore.dll")
        include("vlc-$vlcVersion/plugins/**")
        exclude("vlc-$vlcVersion/plugins/gui/**")
        exclude("vlc-$vlcVersion/plugins/lua/**")
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("appResources/windows-x64/vlc"))
}

tasks.matching {
    it.name in setOf(
        "run",
        "hotRun",
        "packageMsi",
        "packageDeb",
        "packageDmg",
        "createDistributable",
        "packageDistributionForCurrentOS"
    ) || it.name.startsWith("prepareAppResources")
}.configureEach { dependsOn(prepareVlcNatives) }

compose.desktop {
    application {
        mainClass = "com.drdisagree.teledrive.desktop.MainKt"
        providers.gradleProperty("desktopJavaHome").orNull?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
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
                perUserInstall = true
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
