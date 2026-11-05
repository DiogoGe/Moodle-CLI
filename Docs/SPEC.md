# Moodle CLI — Specification

## Goal

Build a local command-line application in Kotlin that helps a learner view Moodle
courses, assignments, and deadlines, then download course materials. The project
should be useful in everyday study and remain understandable as a Kotlin learning project.

The first release supports one user and one Moodle instance. It reads Moodle data
without changing server-side content.

## MVP commands

These commands describe the intended interface, not features already implemented.

| Command | Purpose and acceptance criteria |
| --- | --- |
| `moodle setup <url>` | Validate and save the Moodle base URL locally. Use HTTPS for normal operation. |
| `moodle login` | Authenticate through a supported method and save the verified token in macOS Keychain. Hide secret input. |
| `moodle health` | Report connection health, authentication status, and availability of required API functions. |
| `moodle courses` | List accessible enrolled courses with their IDs and names. |
| `course <id>` | Within the login session, display sections, activity descriptions, inline text, and numbered files. |
| `read <course-id> <module-id>` | Display activity text, including the full content of Moodle Page resources, as plain text. |
| `download <course-id> <module-id> <file-number> [directory]` | Download one listed file without overwriting existing files. |
| `moodle assignments` | Alias of `tasks`; list Moodle Assignment activities separately from quizzes. |
| `moodle quizzes [--course <id>]` | List quizzes and learning checks separately, with IDs and available opening, closing, and time-limit metadata. |
| `moodle tasks` | List accessible assignments with course, title, deadline when present, and submission status when available. |
| `moodle tasks --course <id>` | Restrict results to the selected accessible course. |
| `moodle upcoming --days 7` | Show assignments due within the requested period, ordered by deadline. Reject invalid day counts. |
| `moodle logout` | Clear process state and remove this application's Keychain entry for the configured site. Do not claim server-side token revocation. |
| `moodle --no-store login` | Use process memory only; never read, write, or delete persisted credentials. |
| `moodle --help` | Explain commands and options with examples. |
| `moodle --lang de\|en <command>` | Override the system language for all messages in the process and login session. |

Assignments without deadlines must be labelled accordingly. Submission status must
come from reliable API data; unavailable status is shown as unknown. An elapsed
deadline alone does not prove that an assignment is unsubmitted. Clearly mark past
deadlines in the task list. The upcoming view covers the current time through the
selected future period; older deadlines remain visible through `tasks`.
The window uses elapsed 24-hour days and includes both boundaries. Apply returned
personal extensions before filtering, and use team submission status for group
assignments. Missing optional status access must not hide accessible assignments;
keep it unknown. Mark partial API results and never echo remote warning messages.
`tasks`/`assignments` and `upcoming` cover Moodle Assignment activities. `quizzes`
provides a separate quiz list using `mod_quiz_get_quizzes_by_courses`, optionally
filtered by course. Mark missing timing as unknown; only explicit zero values mean
no restriction. Quiz listing must not create, answer, finish, or fetch questions
from attempts. Other activity types remain available through the course overview.

## Next milestone: course materials

- List course sections, text descriptions, inline content, and downloadable files.
- Download an individual file or the available files of a course.
- Allow a destination directory; organizing bulk downloads by course and section is a later extension.
- Skip existing files by default; require an explicit option to overwrite them.
- Keep downloaded files inside the selected destination, including when remote
  filenames contain unsafe path components.

## Technical decisions

- Kotlin/JVM with Gradle Kotlin DSL and a committed Gradle Wrapper.
- One application module initially.
- Retrofit for API declarations and requests.
- kotlinx.serialization JSON plus the Retrofit serialization converter for JSON mapping.
- OkHttp is brought in by Retrofit; no additional HTTP client is needed initially.
- Kotlin Test with JUnit for automated tests; choose the exact integration during setup.
- Parse the initial commands with `main(args)` and `when`.
- Use JDK APIs for files (`java.nio.file`) and dates and times (`java.time`).
- No Ktor, Clikt, dependency injection framework, or database in the initial setup.
- Select and pin compatible dependency, Kotlin, Gradle, and JDK versions during setup.
- See [Dependency decisions](DEPENDENCIES.md) for the rationale.

## Code organization

Keep a small number of packages with clear responsibilities. Add classes as their
features are implemented, rather than creating empty placeholders.

```text
src/main/kotlin/moodle/
├── Main.kt       # Application entry point
├── cli/          # Command parsing and terminal output
├── config/       # Moodle URL and local settings
├── domain/       # Application models and repository contracts
├── application/  # Workflows, filtering, sorting
└── infrastructure/ # Retrofit API, DTOs, mapping, repository implementation

src/test/kotlin/moodle/
```

A typical flow is `CLI -> service -> repository contract`. The Retrofit repository
implements that contract and calls Moodle. Application logic depends on the
contract, never on Retrofit, response DTOs, or HTTP details. `Main.kt` constructs
and connects the dependencies through constructors. See [Architecture](ARCHITECTURE.md)
for the class diagram and Kotlin conventions.

## Quality requirements

- German and English user-facing messages, with English fallback for other system languages.
  Keep command names and original course content unchanged across languages.
- Readable output for empty results, long names, and missing optional values.
- Clear errors for invalid input, insufficient permissions, expired authentication,
  unsupported functions, and network failures.
- Display deadlines with an explicit date and time in the local time zone.
- Never put passwords or tokens into source control, logs, or error messages.
- Use bounded network timeouts and avoid unbounded retries.
- Tests for deadline filtering, submission status handling, and error handling;
  add download-path tests when downloads are implemented.
- Local automated tests must not require a real Moodle account.
- Build through the Gradle Wrapper and document setup and usage.
- Verify on the development machine first; test other platforms explicitly later.

Persistent authentication uses macOS Keychain through a native adapter. Validate
the token's site before saving and before restoring private access. `exit` keeps a
saved login; `logout` removes it locally. Keep saved credentials on connection
failures, but remove expired or rejected tokens. Store failures are explicit errors,
never a plain-text or silent in-memory fallback. `--no-store` explicitly selects
memory-only operation and is required on other platforms until native adapters exist.

## Moodle integration

Use the Moodle Web Service API as the initial integration path. The user already
has a working Moodle login. During the first real API integration, verify which
API authentication method and functions that account can use. A working browser
login does not by itself establish API access. Report unsupported capabilities
clearly rather than presenting them as empty results.

## Out of scope for the first release

- Assignment submissions, uploads, or other server-side changes.
- Teacher and administrator features.
- Automatically answering quizzes or exams.
- Supporting every Moodle version or institutional authentication method.
- Importing existing browser cookies, scraping course pages, or entering institutional passwords automatically.
  Automatic capture of the final mobile-login callback uses a separate private browser context;
  see [Authentication](AUTHENTICATION.md).
- A graphical interface, hosting, and AI or MCP integration.

## Implementation milestones

1. Set up Kotlin, Gradle Wrapper, dependencies, and the application entry point.
2. Build successfully and provide command help.
3. Display explicitly labelled sample courses to exercise the CLI structure.
4. Connect to the real Moodle API, implement `health`, and replace sample data.
5. Implement course listing, assignments, submission statuses where available,
   and deadline filtering.
6. Verify the MVP and document usage.
7. Add course material downloads.

## Integration details still to determine

- Target Moodle URL and available API authentication method.
- API functions available to the account.
- Native credential-store adapters for platforms other than macOS.
