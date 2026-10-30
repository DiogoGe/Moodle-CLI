plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    application
}

group = "moodle"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaToolchainVersion").get().toInt())
}

application {
    mainClass = "moodle.MainKt"
    applicationName = "moodle"
}

tasks.test {
    inputs.property("chromeIntegration", providers.environmentVariable("MOODLE_CHROME_TESTS").orElse("false"))
    useJUnitPlatform()
}

ktlint {
    version.set(libs.versions.ktlint.engine)
    filter {
        exclude("**/.gradle-user-home/**")
        exclude("**/build/**")
    }
}
