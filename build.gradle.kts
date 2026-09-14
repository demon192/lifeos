// Top-level build file. Plugin versions live in gradle/libs.versions.toml (the "version catalog").
// `apply false` here means: declare the plugin for the whole project, but don't apply it at the root —
// the :app module applies it.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
