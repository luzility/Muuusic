import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val appVersion = (findProperty("versionName") as String?) ?: "0.1.0"

fun msiVersionOf(version: String): String {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?""").matchEntire(version) ?: return "0.0.1"
    val (major, minor, patch, beta) = match.destructured
    return "$major.$minor.${patch.toInt() * 1000 + (beta.toIntOrNull() ?: 999)}"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

val sharedResources = tasks.register<Sync>("sharedResources") {
    from(rootProject.file("app/src/main/res")) {
        include("values*/strings.xml")
        into("strings")
    }
    from(rootProject.file("app/src/main/assets/fonts")) {
        into("fonts")
    }
    from(rootProject.file("app/src/main/res/drawable/ic_launcher_foreground.xml")) {
        into("drawable")
        rename { "logo.xml" }
        filter { line -> line.replace("@color/brand_light", "#FFE9DDFF").replace("@color/brand", "#FF7C4DFF") }
    }
    from(project.file("packaging/muuusic.png")) {
        into("icon")
    }
    into(layout.buildDirectory.dir("generated/sharedResources"))
}

sourceSets.main {
    resources.srcDir(sharedResources)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.desktop.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.material.kolor)
    implementation(libs.sqlite.bundled)
    implementation(libs.org.json)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport)
}

compose.desktop {
    application {
        mainClass = "me.luzility.muuusic.desktop.MainKt"
        jvmArgs += listOf("-Dmuuusic.version=$appVersion", "--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "Muuusic"
            description = "Ad-free YouTube Music client"
            vendor = "Luzi"
            packageVersion = appVersion.substringBefore("-")
            modules("java.sql", "jdk.httpserver", "jdk.unsupported")
            appResourcesRootDir = project.layout.projectDirectory.dir("resources")
            windows {
                iconFile = project.file("packaging/muuusic.ico")
                msiPackageVersion = msiVersionOf(appVersion)
                upgradeUuid = "3393bbc7-efd5-4bc2-a357-8fc6633c92cc"
                menuGroup = "Muuusic"
                perUserInstall = true
                shortcut = true
            }
            linux {
                packageName = "muuusic"
                iconFile = project.file("packaging/muuusic.png")
                menuGroup = "AudioVideo;Audio;Player"
                appCategory = "sound"
                debMaintainer = "firatbirer03@gmail.com"
                shortcut = true
            }
        }
    }
}
