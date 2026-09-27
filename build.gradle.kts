// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // Applied here (not just `apply false`) so it also formats/checks this
    // project's own Kotlin script files (this file, settings.gradle.kts).
    alias(libs.plugins.ktlint)
}

// Standardizes Kotlin formatting across every module via a single `ktlintCheck`/
// `ktlintFormat` entry point at the root, instead of configuring the plugin
// per-module. Uses the "official" Kotlin code style to match `kotlin.code.style`
// in gradle.properties.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
