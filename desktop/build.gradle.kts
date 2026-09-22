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

// The bundled VLC is a Windows-only payload (win64 archive, libvlc.dll). On any
// other host the tasks must not exist, so Linux builds never download the
// Windows archive or bundle DLLs into a non-Windows artifact.
if (System.getProperty("os.name").lowercase().contains("win")) {
    val vlcVersion = "3.0.21"
    val vlcZip = layout.buildDirectory.file("vlc/vlc-$vlcVersion-win64.zip")

    val downloadVlc = tasks.register("downloadVlc") {
        description = "Downloads the VLC natives archive for bundling"
        group = "build"
        val zipFile = vlcZip
        val archiveName = "vlc-$vlcVersion-win64.zip"
        val attempts = 3
        val connectTimeoutMs = 30_000
        val readTimeoutMs = 120_000
        val retryBackoffMs = 5_000L
        val mirrors = listOf(
            "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/$archiveName",
            "https://get.videolan.org/vlc/$vlcVersion/win64/$archiveName"
        )
        outputs.file(zipFile)
        doLast {
            val target = zipFile.get().asFile
            if (target.length() > 0) return@doLast
            target.parentFile.mkdirs()

            var lastFailure: Exception? = null
            repeat(attempts) { attempt ->
                for (mirror in mirrors) {
                    try {
                        val connection = URI(mirror).toURL().openConnection().apply {
                            connectTimeout = connectTimeoutMs
                            readTimeout = readTimeoutMs
                        }
                        connection.getInputStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (target.length() > 0) return@doLast
                    } catch (e: Exception) {
                        lastFailure = e
                        logger.warn("VLC download from $mirror failed: ${e.message}")
                        target.delete()
                    }
                }
                if (attempt < attempts - 1) {
                    Thread.sleep((attempt + 1) * retryBackoffMs)
                }
            }
            throw GradleException("Could not download $archiveName from any mirror", lastFailure)
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
}

val debVersion = libs.versions.appVersion.get()
    .split(".")
    .let { parts -> (parts + List(3) { "0" }).take(3) }
    .joinToString(".")

// The .deb publishes this in its control metadata, so it must be a real address.
// Defaults to the address the maintainer actually uses in this repo's commits
// (DrDisagree <29881338+Mahmud0808@users.noreply.github.com>); override per build
// with -PdebMaintainer=email@example.com. jpackage renders it as "<vendor> <value>",
// so pass a bare email here.
val debMaintainerValue = providers.gradleProperty("debMaintainer")
    .orElse("29881338+Mahmud0808@users.noreply.github.com")

compose.desktop {
    application {
        mainClass = "com.drdisagree.teledrive.desktop.MainKt"
        providers.gradleProperty("desktopJavaHome").orNull?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            packageName = "TeleDrive"
            packageVersion = libs.versions.appVersion.get()
                .split(".")
                .let { parts -> (parts + List(3) { "0" }).take(3) }
                .joinToString(".")
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
                shortcut = true
                packageName = "teledrive"
                appCategory = "Network"
                menuGroup = "Network"
                debMaintainer = debMaintainerValue.get()
            }

            macOS {
                iconFile.set(project.file("icons/TeleDrive.icns"))
                bundleID = "com.drdisagree.teledrive"
            }
        }
    }
}

// Compose's packageDeb cannot declare Debian dependencies, so a dedicated task
// repackages the app image produced by createDistributable (which already
// bundles the JVM runtime via jlink) with jpackage and the system libraries the
// TDLight native needs at runtime. jpackage's generated postinst calls
// xdg-desktop-menu under set -e and fails on minimal systems, so the control
// scripts are replaced with versions that degrade to a direct desktop-file
// install when no menu directory exists.
val packageDebUser = tasks.register("packageDebUser") {
    description = "Builds an installable .deb with declared system dependencies and a robust postinst via jpackage"
    group = "distribution"
    dependsOn("createDistributable")
    notCompatibleWithConfigurationCache("runs jpackage and dpkg-deb from a doLast closure")
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/TeleDrive")
    val destDir = layout.buildDirectory.dir("deb")
    val workDir = layout.buildDirectory.dir("debwork")
    val javaHome = providers.gradleProperty("desktopJavaHome").orNull
    val jpackage = javaHome?.let { File(it, "bin/jpackage") }
        ?: File(System.getProperty("java.home"), "bin/jpackage")
    val debFile = destDir.map { it.file("teledrive_${debVersion}_amd64.deb") }
    inputs.dir(appImage)
    outputs.file(debFile)
    doLast {
        providers.exec {
            commandLine(
                jpackage.absolutePath,
                "--type", "deb",
                "--app-image", appImage.get().asFile.absolutePath,
                "--name", "TeleDrive",
                "--app-version", debVersion,
                "--vendor", "DrDisagree",
                "--linux-package-name", "teledrive",
                "--linux-package-deps", "libssl3t64,zlib1g,libstdc++6,libgcc-s1,libc6",
                "--linux-shortcut",
                "--linux-app-category", "Network",
                "--linux-menu-group", "Network",
                "--linux-deb-maintainer", debMaintainerValue.get(),
                "--dest", destDir.get().asFile.absolutePath
            )
        }.result.get()

        val work = workDir.get().asFile
        work.deleteRecursively()
        providers.exec {
            commandLine("dpkg-deb", "-R", debFile.get().asFile.absolutePath, work.absolutePath)
        }.result.get()
        File(work, "DEBIAN/postinst").apply {
            writeText(ROBUST_POSTINST)
            setExecutable(true)
        }
        File(work, "DEBIAN/postrm").apply {
            writeText(ROBUST_POSTRM)
            setExecutable(true)
        }
        providers.exec {
            commandLine("dpkg-deb", "--build", work.absolutePath, debFile.get().asFile.absolutePath)
        }.result.get()
        work.deleteRecursively()
    }
}

val ROBUST_POSTINST = """#!/bin/sh
# postinst script for teledrive
set -e
case "${'$'}1" in
    configure)
        desktop=/opt/teledrive/lib/teledrive-TeleDrive.desktop
        if [ ! -f "${'$'}desktop" ]; then exit 0; fi
        if xdg-desktop-menu install "${'$'}desktop" 2>/dev/null; then
            :
        else
            mkdir -p /usr/share/applications
            cp "${'$'}desktop" /usr/share/applications/teledrive.desktop
        fi
        ;;
    abort-upgrade|abort-remove|abort-deconfigure)
        ;;
    *)
        echo "postinst called with unknown argument \`${'$'}1'" >&2
        exit 1
        ;;
esac
exit 0
"""

val ROBUST_POSTRM = """#!/bin/sh
# postrm script for teledrive
set -e
case "${'$'}1" in
    remove|purge)
        rm -f /usr/share/applications/teledrive.desktop
        xdg-desktop-menu uninstall /opt/teledrive/lib/teledrive-TeleDrive.desktop 2>/dev/null || true
        ;;
    upgrade|failed-upgrade|abort-install|abort-upgrade|disappear)
        ;;
    *)
        echo "postrm called with unknown argument \`${'$'}1'" >&2
        exit 1
        ;;
esac
exit 0
"""
