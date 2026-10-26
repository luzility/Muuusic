import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val appVersion = (findProperty("versionName") as String?) ?: "0.1.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
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
}

compose.desktop {
    application {
        mainClass = "me.luzility.muuusic.desktop.MainKt"
        jvmArgs += listOf("-Dmuuusic.version=$appVersion", "--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "Muuusic"
            packageVersion = appVersion.substringBefore("-")
            modules("java.sql", "jdk.httpserver", "jdk.unsupported")
        }
    }
}
