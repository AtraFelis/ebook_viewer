plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.example.ebookviewer.searchprobe"
    compileSdk = 35
    buildToolsVersion = "34.0.0"
    defaultConfig {
        applicationId = "dev.example.ebookviewer.searchprobe"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1-diagnostic"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        warningsAsErrors = true
    }
    sourceSets.getByName("main").assets.srcDir(
        rootProject.file("local-artifacts/search-probe-fixtures")
    )
}

// A separate diagnostic APK, never a release artifact or a reader runtime dependency.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant -> variant.enable = false }
}

tasks.named("preBuild") {
    doFirst {
        check(rootProject.file("local-artifacts/search-probe-fixtures/manifest.json").isFile) {
            "Run scripts/generate-search-probe-fixtures.py before building this diagnostic APK."
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}
