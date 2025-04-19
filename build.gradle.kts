plugins {
    // Keep AGP and Kotlin in the same plugin classloader for built-in Kotlin support.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "io.github.siarheisinelnikau"
    version = "2.0.0-SNAPSHOT"
}
