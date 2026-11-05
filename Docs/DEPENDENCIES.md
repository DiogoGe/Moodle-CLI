# Dependency decisions

## Initial dependencies

| Dependency | Responsibility |
| --- | --- |
| Retrofit | Describe Moodle requests through Kotlin interfaces and execute them. |
| kotlinx.serialization JSON | Convert JSON responses into Kotlin data classes. |
| Retrofit kotlinx.serialization converter | Connect Retrofit response conversion to Kotlin serialization. |
| JNA | Call the native macOS Keychain API without shell commands or secret command-line arguments. |
| jsoup | Parse course HTML locally into readable terminal text without executing scripts or fetching resources. |
| Kotlin Test with JUnit | Support automated tests. |

Retrofit brings in OkHttp as a transitive dependency. We do not need Ktor or a
second HTTP client. Kotlin serialization also requires its Kotlin compiler plugin;
configure that plugin alongside the JSON runtime library.

Dependency and plugin versions are defined in `gradle/libs.versions.toml`.
Project and Java toolchain versions are in `gradle.properties`; the Gradle version
is in `gradle/wrapper/gradle-wrapper.properties`. Keep exact versions in those
configuration files rather than duplicating them here.

## Functionality provided without additional libraries

| Concern | Initial approach |
| --- | --- |
| Command parsing | `main(args: Array<String>)` and `when` |
| Terminal output | `println` and small formatting functions |
| Files and local configuration | JDK `java.nio.file` and a simple configuration format |
| Deadlines and time zones | JDK `java.time` |

Clikt, a dependency injection framework, a database, and a logging framework are
not required for the initial implementation. Add dependencies when a concrete
requirement justifies them.

## Credential storage

The macOS adapter uses JNA and Apple's SecItem API. Native access is enabled in the
generated launcher. The application does not depend on a generic keyring wrapper
that might invoke external commands or select a fallback backend. Other operating
systems currently use explicit memory-only mode. Never treat ordinary configuration
files as secure credential storage.

## References

- [Retrofit](https://github.com/square/retrofit)
- [Kotlin serialization](https://kotlinlang.org/docs/serialization-get-started.html)
- [JDK HTTP client](https://docs.oracle.com/en/java/javase/24/docs/api/java.net.http/java/net/http/HttpClient.html), an alternative if we later choose to remove Retrofit.
