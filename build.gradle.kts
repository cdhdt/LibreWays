// Root build script. No production code lives here — it only declares plugins so they resolve
// once and are applied per-module. See docs/adr/010-module-layout.md for why :domain and :app
// are separate Gradle modules rather than packages inside one.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    // Applied (not apply false) at the root so this file and settings.gradle.kts are linted too.
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt) apply false
}
