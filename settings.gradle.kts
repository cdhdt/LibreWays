pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle auto-provision a JDK toolchain (see the `jvmToolchain(21)` calls in each
    // module) on any machine, rather than relying on whatever JDK happens to be on PATH. Needed
    // in practice because detekt 1.23.8's bundled compiler frontend does not yet parse very new
    // JDK version strings (verified against JDK 25 in this project's own build environment) —
    // pure build-tooling plumbing, not a product/runtime dependency.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
