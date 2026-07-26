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
    }
}

rootProject.name = "LibreWays"

// :domain is pure Kotlin/JVM — no Android Gradle Plugin applied, no Android dependency at all
// (ADR 010). Importing android.* there is a compile error, not a review finding.
include(":domain")

// :app is the Android application module. It stays bare: no UI toolkit (ADR 001 is still open),
// no map, no HTTP client — only what an Android application module structurally requires.
include(":app")
