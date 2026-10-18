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

rootProject.name = "EbookViewer"
check(gradle.gradleVersion == "8.9") {
    "This repository requires the approved Gradle 8.9 toolchain (ADR 0001)."
}
include(":app")
include(":searchprobe")
