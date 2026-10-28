plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    // Not applied (AGP 9 has built-in Kotlin); declared only to pin the Kotlin Gradle plugin version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.licensee) apply false
}
