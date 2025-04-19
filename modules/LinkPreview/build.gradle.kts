import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifactId = "link-preview"
            pom {
                name.set("LinkPreview")
                description.set("Coroutine-first link metadata extraction for Android and Kotlin/JVM")
                url.set("https://github.com/siarheisinelnikau/LinkPreview")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
