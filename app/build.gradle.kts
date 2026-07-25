// The Android application module. Deliberately bare: ADR 001 (UI toolkit) is still open, so no
// Compose, no view binding, no navigation library is added here — nothing that presumes an
// undecided choice. This module exists to hold the manifest and, eventually, the composition root
// wiring :domain to concrete :data implementations once those modules/ADRs exist.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    // "com.example.libreways" is a deliberate placeholder, not a decision: the application id is
    // an open question (CLAUDE.md §0.2) the human has not yet settled, and it is effectively
    // permanent once published. Replace it before any real release; see the PR body.
    namespace = "com.example.libreways"
    compileSdk =
        libs.versions.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.example.libreways"
        minSdk =
            libs.versions.min.sdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.target.sdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // Explicit, matching :domain's jvmToolchain(21) — a build-tooling constraint (see
        // gradle.properties on why auto-download is disabled), not a product decision.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":domain"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}
