# Architecture

## Dependency boundaries

Use one Gradle module with separate packages. The domain owns the models and the
contract for retrieving Moodle data. Application services depend on this contract.
Infrastructure implements it using Retrofit. The CLI handles input and presentation.

```mermaid
classDiagram
    MoodleCli --> CourseCommands
    CourseCommands --> CourseService
    CourseService --> AccessService
    CourseService --> CourseRepository
    CourseService --> CourseFiles
    RetrofitCourseRepository ..|> CourseRepository
    LocalCourseFiles ..|> CourseFiles
    LocalCourseFiles --> MoodleFileClient
    RetrofitCourseRepository --> MoodleApi
    CourseRepository ..> Course
    CourseRepository ..> CourseSection
    CourseSection *-- CourseModule
    CourseModule *-- CourseFile
```

`CourseService` handles authenticated course operations and selection of activities
and files. `AccessService.withSession` supplies the verified user ID and in-memory
token, and removes credentials when an operation reports expired authentication.
`CourseRepository` and `CourseFiles` are replaceable boundaries. Application services
do not depend on Retrofit, response DTOs, filesystem APIs, or terminal output.
`AssignmentService` depends on `AssignmentRepository` and `AccessService` for
assignment listing and optional submission details. `RetrofitAssignmentRepository`
uses the capabilities recorded at login to skip unsupported status requests.
`AssignmentList.partial` preserves API warnings without retaining their remote text.
Individual extensions are applied before deadline filtering; `Clock` makes window
boundaries and local-time presentation deterministic. `StudyCommands` dispatches
course and assignment commands consistently in both CLI entry paths.

`RetrofitCourseRepository` maps course DTOs into domain models. `MoodleFileClient`
restricts credential-bearing requests to the configured Moodle file endpoint and
sends the token in the POST body without following redirects. `LocalCourseFiles`
handles bounded streaming, safe filenames, exclusive file creation, and cleanup
of failed downloads. `TerminalText` parses HTML locally with jsoup and removes
terminal controls; it does not fetch embedded resources.

`Main.kt` is the composition root: it loads configuration, builds adapters and
services, and connects the CLI dispatchers. Secrets stay in the running process.
Keep secrets out of generated `toString()` output, including configuration data classes.

## Constructor injection

Pass dependencies explicitly through constructors. No dependency injection library
or global service locator is required. The following is a design sketch:

```kotlin
class AssignmentService(
    private val access: AccessService,
    private val repository: AssignmentRepository,
    private val clock: Clock
)
```

Use the JDK `Clock` to make deadline filtering deterministic in tests. Tests supply
a fake repository and a fixed clock without contacting Moodle. The production
repository is separately responsible for API response mapping and error translation.

An interface is useful at an external boundary that we need to replace in tests.
Ordinary services and models remain concrete classes. Split the repository contract
by feature if authentication, downloads, or other capabilities make it too broad.

## Kotlin implementation conventions

- Prefer `val`, read-only collection interfaces, and data classes for domain values.
  Read-only collections do not by themselves guarantee immutable backing storage.
- Model missing values with nullable types; avoid `!!`.
- Use primary constructors and private dependencies; use properties instead of
  Java-style accessor methods.
- Keep functions focused, with explicit return types at architectural boundaries.
- Use top-level functions for small stateless helpers and extension functions only
  when they improve readability.
- Prefer composition; Kotlin classes remain final unless inheritance is needed.
- Represent distinct application failures with a small sealed hierarchy when error
  handling is implemented. Choose one consistent propagation approach, and do not
  turn failures into empty result lists.
- Begin with sequential requests and synchronous repository methods. Introduce
  coroutines when asynchronous work is required, consistently across the call path.
- Keep output in the CLI and networking in infrastructure. Allow the CLI output
  function to be supplied in tests when output verification is needed.

Follow the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
and [data class documentation](https://kotlinlang.org/docs/data-classes.html).

## Scope

Keep the initial design small: no interface for every class, generic base repository,
or separate Gradle module per layer. Add credential storage and download boundaries
when those features are implemented.

## Browser login responsibilities

`BrowserLoginService` validates the login attempt and callback; `LoginAttempt` is
its separate state type. The browser implementation has three concrete parts:

- `IsolatedChromeLoginBrowser` coordinates opening a page and waiting for the callback.
- `ChromeProcess` owns the process, private pipes, temporary profile, and cleanup.
- `ChromeLoginPage` configures one browser tab and reads its expected callback link.

`ChromePipe` handles bounded protocol messages and response matching. `MoodleCallbackPage`
contains the scoped capture scripts. These classes keep transport and process details
inside infrastructure. The private browser, callback validation, and credential
lifetime remain as described in [Authentication](AUTHENTICATION.md).

API DTOs live under `infrastructure/api/dtos`. Mapping to domain types is kept in
`ApiMappers.kt`. Domain enums, including `SubmissionStatus`, belong under `domain/enums`;
domain entities must not depend on infrastructure.

## Localized presentation

The CLI owns `Messages`, which loads JDK `ResourceBundle` properties and formats
parameterized text with `MessageFormat`. English resources are the fallback;
German resources cover German locales. Global `--lang de|en` is parsed at startup
and one `Messages` instance is injected into CLI components. The JVM's global
locale is never changed.

`MoodleException` carries a stable message key and safe formatting arguments.
Domain, application, and infrastructure code do not load resources or select a
language. The CLI translates errors at the output boundary. Programmer assertions
and protocol identifiers stay separate from user-facing text. Remote Moodle
messages are still sanitized rather than displayed directly. IDs are formatted
without digit grouping so they can be copied into commands.

When adding a message, add the same key and numbered placeholders to both resource
files. Keep properties UTF-8; escape literal apostrophes as `''` for MessageFormat.
Course content is not translated. Command names are the same in both languages.
