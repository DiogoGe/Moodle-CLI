# Dependency decisions

[Project overview](../README.md) · [Development](DEVELOPMENT.md) · [Architecture](ARCHITECTURE.md)

## Runtime and test dependencies

| Dependency | Responsibility |
| --- | --- |
| Retrofit | Describe Moodle requests through Kotlin interfaces and execute them. |
| kotlinx.serialization JSON | Convert JSON responses into Kotlin data classes. |
| Retrofit kotlinx.serialization converter | Connect Retrofit response conversion to Kotlin serialization. |
| JNA and JNA Platform | Native macOS/Windows credential APIs and Windows browser pipes without secret command-line arguments. |
| jsoup | Parse course HTML locally into readable terminal text without executing scripts or fetching resources. |
| SLF4J NOP | Disable SDK logging so protocol output and private request details are not written to logs. |
| MCP Kotlin server SDK | MCP protocol, schemas, and stdio lifecycle for the optional local Codex integration. |
| Kotlin Test with JUnit | Support automated tests. |

Retrofit brings in OkHttp as a transitive dependency. We do not need Ktor or a
second Moodle HTTP client. Kotlin serialization also requires its Kotlin compiler plugin;
configure that plugin alongside the JSON runtime library.

Dependency and plugin versions are defined in `gradle/libs.versions.toml`.
Project and Java toolchain versions are in `gradle.properties`; the Gradle version
is in `gradle/wrapper/gradle-wrapper.properties`. Keep exact versions in those
configuration files rather than duplicating them here.

## Functionality provided without additional libraries

| Concern | Approach |
| --- | --- |
| Command parsing | `main(args: Array<String>)` and `when` |
| Terminal output | `println` and small formatting functions |
| Files | JDK `java.nio.file` |
| Deadlines and time zones | JDK `java.time` |

Clikt, a dependency injection framework, a database, and a logging framework are
not required for the initial implementation. Add dependencies when a concrete
requirement justifies them.

## Credential storage

The macOS adapter uses JNA and Apple's SecItem API. The Windows adapter uses
Credential Manager; JNA Platform supplies Windows ABI types for credentials and
private browser pipes. Native access is enabled in the generated launcher and
executable JAR manifest. The application does not depend on a generic keyring
wrapper that might invoke external commands or select a fallback backend. Linux
currently uses explicit memory-only mode. Never treat ordinary configuration files
as secure credential storage.

## Standalone application packaging

The [Shadow Gradle plugin](https://gradleup.com/shadow/) packages the application
and its runtime dependencies into one executable JAR. Service provider descriptors
are merged, bundled native libraries are retained, and dependency notices are
preserved. Do not enable minimization: serialization, reflection, and native resource
loading require classes and resources that static reachability analysis may miss.
The manifest enables native access for `java -jar`; the Java runtime remains an
external prerequisite. Plugin versions stay in the version catalog.

## References

- [Retrofit](https://github.com/square/retrofit)
- [Kotlin serialization](https://kotlinlang.org/docs/serialization-get-started.html)
- [JDK HTTP client](https://docs.oracle.com/en/java/javase/24/docs/api/java.net.http/java/net/http/HttpClient.html), an alternative if we later choose to remove Retrofit.

## Local MCP protocol

The official MCP Kotlin server SDK owns protocol negotiation, transport framing,
capability handling, and stdio shutdown. Its coroutine and Ktor modules are
transitive dependencies; this project does not start a Ktor HTTP server or use it
as another Moodle API client. Retrofit remains the Moodle transport. The local
plugin needs no OpenAI SDK or API key; Codex is the MCP client.
