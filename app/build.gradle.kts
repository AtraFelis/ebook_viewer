plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.example.ebookviewer"
    compileSdk = 35
    buildToolsVersion = "34.0.0"
    testBuildType = providers.gradleProperty("p0TestBuildType").getOrElse("debug")
    buildTypes {
        create("profile") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    defaultConfig {
        applicationId = "dev.example.ebookviewer.p0"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-p0"
        testInstrumentationRunner = "dev.example.ebookviewer.P0Instrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Generated public synthetic data only; never package local documents in the reader APK.
    sourceSets.getByName("androidTest").assets.srcDir(
        rootProject.file("local-artifacts/sharpness-fixtures")
    )
    lint {
        warningsAsErrors = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
}

dependencyLocking {
    lockAllConfigurations()
}
