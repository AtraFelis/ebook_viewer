plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

val ktlint by configurations.creating {
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}
dependencies {
    ktlint(libs.ktlint)
}

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin source and Gradle scripts without modifying them."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("**/*.kt", "**/*.kts", "!**/build/**", "!**/.gradle/**", "!local-artifacts/**")
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Explicitly formats Kotlin source and Gradle scripts."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("--format", "**/*.kt", "**/*.kts", "!**/build/**", "!**/.gradle/**", "!local-artifacts/**")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.wrapper {
    gradleVersion = "8.9"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
}
