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
    implementation(libs.mcp.server)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.slf4j.nop)
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

// Carry the project's license alongside dependency notices in each application JAR.
tasks.withType<Jar>().configureEach {
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF/moodle-cli")
    }
}

val prepareCodexPlugin = tasks.register<Sync>("prepareCodexPlugin") {
    description = "Build a local Codex marketplace containing the Moodle plugin and its MCP server."
    group = "distribution"
    dependsOn(tasks.shadowJar)
    into(layout.buildDirectory.dir("codex-plugin"))
    from("plugins/moodle") {
        into("plugins/moodle")
        filesMatching("plugin.json") {
            filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("pluginVersion" to project.version.toString()))
        }
    }
    from(tasks.shadowJar.flatMap { it.archiveFile }) {
        into("plugins/moodle/server")
    }
    from("plugin-packaging/marketplace.json") {
        into(".agents/plugins")
    }
    from("LICENSE") {
        into("plugins/moodle")
    }
}

val codexPluginZip = tasks.register<Zip>("codexPluginZip") {
    description = "Package the local Moodle Codex marketplace for sharing."
    group = "distribution"
    dependsOn(prepareCodexPlugin)
    from(prepareCodexPlugin)
    archiveFileName.set("moodle-codex-plugin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
