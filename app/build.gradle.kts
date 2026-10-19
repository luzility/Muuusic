plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

// CI passes -PversionName from the git tag (v1.2.0 or v1.2.0-beta.3); local builds use the default.
val appVersion = (findProperty("versionName") as String?) ?: "0.1.0"

// 1.2.0-beta.3 -> 1020003, 1.2.0 -> 1020099, so a release always sorts after its betas.
fun versionCodeOf(version: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?$""").matchEntire(version)
        ?: error("Version must look like 1.2.0 or 1.2.0-beta.3: $version")
    val (major, minor, patch, beta) = match.destructured
    return ((major.toInt() * 100 + minor.toInt()) * 100 + patch.toInt()) * 100 + (beta.toIntOrNull() ?: 99)
}

android {
    namespace = "me.luzility.muuusic"
    compileSdk = 37

    defaultConfig {
        applicationId = "me.luzility.muuusic"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    signingConfigs {
        // Set by the release workflow; without it release builds fall back to the debug key.
        System.getenv("SIGNING_KEYSTORE")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    // Lists every values-xx folder as an app language, so new translations show up in Android's language settings.
    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.media3.common.util.UnstableApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.material.kolor)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)
    implementation(libs.reorderable)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Installs the bundled baseline profile on devices that don't get it from Play.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    // Real org.json for JVM tests; android.jar only has stubs.
    testImplementation(libs.org.json)
}
