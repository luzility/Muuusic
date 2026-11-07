pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
        maven("https://jogamp.org/deployment/maven") {
            content { includeGroup("org.jogamp.gluegen"); includeGroup("org.jogamp.jogl") }
        }
    }
}

rootProject.name = "Muuusic"
include(":app")
include(":core")
include(":desktop")
include(":baselineprofile")
