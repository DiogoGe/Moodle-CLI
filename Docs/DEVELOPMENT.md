# Development

[Project overview](../README.md) · [Architecture](ARCHITECTURE.md) · [Dependency decisions](DEPENDENCIES.md)

## Open and build the project

Use a JDK matching `javaToolchainVersion` in [gradle.properties](../gradle.properties).
Open the repository root as a Gradle project in IntelliJ IDEA, or use the committed
Gradle Wrapper. A separate Kotlin or Gradle installation is not required.

```sh
./gradlew build
./gradlew run --args="--help"
```

On Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`.
Use a real terminal and an installed distribution or standalone JAR for interactive
login; Gradle and IDE run consoles may not expose the hidden-input console.

## Format and check

Follow [.editorconfig](../.editorconfig), Kotlin conventions, and the design rules
in [Architecture](ARCHITECTURE.md). Formatting is explicit; `build` checks it but
does not automatically rewrite source files.

```sh
./gradlew ktlintFormat
./gradlew check
```

Run formatting first, then checks as a separate command. `check` runs style checks
and tests. `./gradlew ktlintCheck` checks formatting alone; `./gradlew test` runs
tests alone. Avoid trailing commas and keep lines within the configured limit.
Prefer focused functions, constructor injection, and replaceable boundary interfaces.

## Tests

Normal tests use fixtures, fake repositories, a fixed clock where needed, and local
HTTP servers. They do not require a real Moodle account. Native browser and
credential integration tests are opt-in and use invented credentials.

See [Authentication verification](AUTHENTICATION.md#verification) for the macOS and
browser test commands, and [Windows native integration](AUTHENTICATION.md#windows-native-integration)
for Windows commands. Run the relevant native tests on the actual target platform;
a simulated native-API unit test does not establish native runtime compatibility.

The existing GitHub Actions workflow runs the build, style checks, and tests on
push and pull requests. Its configuration is in
[ci.yml](../.github/workflows/ci.yml).

## Generated launchers

```sh
./gradlew installDist
./build/install/moodle/bin/moodle --help
```

Windows PowerShell:

```powershell
.\gradlew.bat installDist
.\build\install\moodle\bin\moodle.bat --help
```

The generated distribution contains launchers and a `lib` directory. Keep them
together when copying a distribution. Launchers enable the native access needed
for browser and credential adapters.

## Standalone JAR

```sh
./gradlew shadowJar
java -jar build/libs/moodle.jar --help
```

Windows PowerShell:

```powershell
.\gradlew.bat shadowJar
java -jar .\build\libs\moodle.jar --help
```

The output is `build/libs/moodle.jar`. `build` also creates it. The Shadow plugin
bundles the application, Kotlin runtime, dependency libraries, native resources,
service-provider metadata, and license notices. Native access is enabled through
the executable JAR's manifest. Do not enable minimization; reflection and native
resource loading need resources not necessarily discovered by static analysis.

Share the JAR without the source checkout or Gradle cache. Recipients still need
a compatible Java runtime and Chrome for automatic login. The JAR does not bundle
Java or include local credentials, downloaded course materials, or browser profiles.
The same artifact includes macOS and Windows adapters; Linux uses `--no-store`.
Do not describe Windows runtime support as verified without running the native tests
on Windows. See [Dependency decisions](DEPENDENCIES.md#standalone-application-packaging).

## Codex plugin bundle

Run `./gradlew prepareCodexPlugin codexPluginZip` to build the local plugin marketplace
and shareable ZIP. Plugin templates live under `plugins/moodle/`; the generated bundle
contains a copy of the standalone JAR and takes its version from build configuration.
See [MCP](MCP.md) for installation, tool behavior, authentication, and validation.

## Source layout

```text
src/main/kotlin/moodle/
├── Main.kt          # Composition root and entry point
├── cli/             # Commands, localization, and terminal presentation
├── mcp/             # MCP tool schemas, bounded output, and stdio presentation
├── presentation/    # Shared local HTML-to-text conversion
├── application/     # Authentication and study workflows, filtering, sorting
├── domain/          # Values, entities, exceptions, and boundary contracts
├── infrastructure/  # HTTP adapters, browser integration, credentials, and files
└── config/          # Connection configuration

src/main/resources/i18n/  # English and German messages
src/test/kotlin/moodle/   # Tests and fixtures
```

Application and domain code remain independent of Retrofit, DTOs, filesystem APIs,
and terminal output. `Main.kt` wires dependencies through constructors. Consult
[Architecture](ARCHITECTURE.md) before changing a boundary.

## Version configuration

| Configuration | Location |
| --- | --- |
| Application version and Java toolchain | [gradle.properties](../gradle.properties) |
| Kotlin, plugins, and library versions | [Version catalog](../gradle/libs.versions.toml) |
| Gradle distribution | [Wrapper properties](../gradle/wrapper/gradle-wrapper.properties) |

Keep exact versions in these files rather than duplicating them in documentation.

## Contributing

Propose changes through a pull request. The maintainer decides what to merge;
public visibility and the MIT license do not grant direct repository write access.
Keep changes focused, format edited Kotlin files, and run `check` before submitting.

Never include tokens, callback links, passwords, personal account data, or non-public
course materials in source, fixtures, logs, issues, screenshots, or pull requests.
Use invented values and local fixtures for tests. Ignoring a file in Git does not
make it secure credential storage.

Write documentation in English. Keep the README as an entry point and put detailed
instructions in the matching guide. Update documentation when setup, public
behavior, requirements, or architectural decisions change; report routine progress
and individual verification results in the pull request or chat instead.
