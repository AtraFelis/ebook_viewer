plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val releaseSigningEnabled = providers.gradleProperty("releaseSigning")
    .map { it.toBooleanStrict() }.getOrElse(false)
val releaseSigningValues = if (releaseSigningEnabled) {
    listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").associateWith { name ->
        providers.environmentVariable("EBOOK_RELEASE_$name").orNull
            ?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Missing EBOOK_RELEASE_$name for signed release")
    }.also {
        if (!file(it.getValue("STORE_FILE")).isFile) {
            throw GradleException("Release keystore file is missing")
        }
    }
} else {
    emptyMap()
}

android {
    namespace = "dev.example.ebookviewer"
    compileSdk = 35
    buildToolsVersion = "34.0.0"
    testBuildType = providers.gradleProperty("p0TestBuildType").getOrElse("debug")
    signingConfigs {
        if (releaseSigningEnabled) {
            create("personalRelease") {
                storeFile = file(releaseSigningValues.getValue("STORE_FILE"))
                storeType = "PKCS12"
                storePassword = releaseSigningValues.getValue("STORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".p0"
            versionNameSuffix = "-dev"
        }
        getByName("release") {
            if (releaseSigningEnabled) signingConfig = signingConfigs.getByName("personalRelease")
        }
        create("profile") {
            initWith(getByName("release"))
            applicationIdSuffix = ".p0"
            versionNameSuffix = "-profile"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    defaultConfig {
        applicationId = "dev.example.ebookviewer"
        minSdk = 23
        targetSdk = 35
        versionCode = providers.gradleProperty("ebook.versionCode").get().toInt()
        versionName = providers.gradleProperty("ebook.versionName").get()
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
