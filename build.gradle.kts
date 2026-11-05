plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.shadow)
    application
}

group = "moodle"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jna)
    implementation(libs.jna.platform)
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
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    inputs.property("chromeIntegration", providers.environmentVariable("MOODLE_CHROME_TESTS").orElse("false"))
    useJUnitPlatform()
    inputs.property("keychainIntegration", providers.environmentVariable("MOODLE_KEYCHAIN_TESTS").orElse("false"))
    inputs.property("windowsCredentials", providers.environmentVariable("MOODLE_WINDOWS_CREDENTIAL_TESTS").orElse("false"))
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

ktlint {
    version.set(libs.versions.ktlint.engine)
    filter {
        exclude("**/.gradle-user-home/**")
        exclude("**/build/**")
    }
}

// Keep reflection, service providers, and JNA's platform binaries in the standalone artifact.
tasks.shadowJar {
    archiveFileName.set("moodle.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
    val notices = listOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")
    notices.forEach { path ->
        filesMatching(path) { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
        append(path)
    }
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/SIG-*")
    manifest {
        attributes(
            "Enable-Native-Access" to "ALL-UNNAMED",
            "Implementation-Version" to project.version.toString()
        )
    }
    failOnDuplicateEntries = true
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
