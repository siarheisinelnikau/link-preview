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

rootProject.name = "LinkPreview"

include(":link-preview", ":link-preview-okhttp")
project(":link-preview").projectDir = file("modules/LinkPreview")
project(":link-preview-okhttp").projectDir = file("modules/LinkPreviewOkHttp")

// The library and its tests can also be built on machines without an Android SDK.
if (providers.gradleProperty("includeAndroidSample").getOrElse("true").toBoolean()) {
    include(":app")
}
