// Pure Kotlin/JVM module. Deliberately no Android Gradle Plugin applied and no Android
// dependency of any kind: `import android.*` here is a compile error, not a review finding
// (docs/adr/010-module-layout.md). Test dependencies are exactly ADR 012's decision.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    // Pinned to a level detekt's embedded compiler accepts — a build-tooling constraint, not a
    // product decision; unrelated to any §0.2 open decision. Requires a JDK 21 installation on
    // the machine running this build (auto-download is disabled, see gradle.properties) — see
    // docs/testing.md for where to get one.
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnit()
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}
