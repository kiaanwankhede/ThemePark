pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "ThemePark"

// Pure-Kotlin game rules shared by the app and the home-screen widget.
include(":core")

// Android app, including the home-screen widget.
include(":app")
