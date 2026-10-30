# Dependency decisions

## Initial dependencies

| Dependency | Responsibility |
| --- | --- |
| Retrofit | Describe Moodle requests through Kotlin interfaces and execute them. |
| kotlinx.serialization JSON | Convert JSON responses into Kotlin data classes. |
| Retrofit kotlinx.serialization converter | Connect Retrofit response conversion to Kotlin serialization. |
| Kotlin Test with JUnit | Support automated tests. |

Retrofit brings in OkHttp as a transitive dependency. We do not need Ktor or a
second HTTP client. Kotlin serialization also requires its Kotlin compiler plugin;
configure that plugin alongside the JSON runtime library.

Dependency and toolchain versions are defined in `build.gradle.kts` and
`gradle/wrapper/gradle-wrapper.properties`. Keep version numbers in those build
files rather than duplicating them here.

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

The minimal dependency list does not resolve secure persistent token storage.
Choose an operating-system credential-store integration when implementing
persistent login; an additional library may be appropriate. Never treat ordinary
configuration-file storage as secure storage for a token.

## References

- [Retrofit](https://github.com/square/retrofit)
- [Kotlin serialization](https://kotlinlang.org/docs/serialization-get-started.html)
- [JDK HTTP client](https://docs.oracle.com/en/java/javase/24/docs/api/java.net.http/java/net/http/HttpClient.html), an alternative if we later choose to remove Retrofit.
