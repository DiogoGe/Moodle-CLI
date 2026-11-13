# Moodle CLI — Specification

[Project overview](../README.md) · [Usage](USAGE.md) · [Architecture](ARCHITECTURE.md)

## Goal and scope

Provide a local Kotlin command-line application that helps a learner browse Moodle
courses and materials, inspect assignments and quizzes, and download individual
files. Keep the code understandable as a Kotlin learning project.

The application supports one user and one configured Moodle instance per process.
The current instance is `https://moodle.zhaw.ch/`. Institutional authentication uses
the Moodle mobile login flow with SWITCH authentication completed by the user.
The application reads Moodle data without modifying server-side course content.

This document defines requirements and boundaries. [Usage](USAGE.md) documents the
available command syntax; [Getting started](GETTING_STARTED.md) covers installation
and sign-in. Future extensions are listed separately below.

## Authentication and session requirements

- Support automatic callback capture through an isolated browser and manual callback
  input through a real terminal's hidden prompt.
- Bind callbacks to the current site and a single-use, time-limited login attempt.
- Validate the token with Moodle before saving or activating a session.
- Provide `health` to report public Web Service configuration, authenticated status,
  and relevant functions exposed by the token.
- Store verified tokens in macOS Keychain or Windows Credential Manager through
  native adapters. Report storage failures explicitly; never silently fall back
  to an ordinary file or memory-only mode.
- Restore and verify a saved token before private feature requests. Keep saved
  credentials on network failures; remove expired or rejected tokens.
- `exit` ends the interactive process and retains a saved login. `logout` also removes
  the application's saved credential for the configured site, without claiming
  server-side token revocation.
- `--no-store` must bypass all persisted credential reads, writes, and deletions.
  Linux currently requires this explicit memory-only mode.

See [Authentication](AUTHENTICATION.md) for browser isolation, token transport,
native resource ownership, and the limitations of OS credential storage.

## Course and material requirements

- List enrolled courses with IDs and names.
- Search enrolled course titles, accessible activity names, and file names by
  keyword. Return file selectors and report denied course-content access.
- Open a selected activity in the default browser using its course and module
  IDs. Build the link from the configured site and validated module metadata.
- Show accessible sections, descriptions, inline text, activities, and numbered files.
- Read activity text, including the full body of accessible Moodle Page resources.
- Convert HTML to terminal-safe plain text without executing scripts or fetching
  embedded resources during rendering.
- Download one file selected by course ID, course-module ID, and displayed file number.
- Download up to 10 selected files in one operation, reporting individual outcomes.
- Optionally group a batch under stable course and activity folders. Refuse
  linked subdirectories so organized downloads stay within the destination.
- Accept an optional destination directory and default to `./downloads`.
- Never overwrite an existing file or symbolic link. There is no overwrite option.
- Reject unsafe path components, terminal controls, and reserved Windows device names.
- Bound file downloads to 512 MiB and text reads to 2 MiB. Restrict credential-bearing
  file requests to the configured Moodle file endpoint without following redirects.
- Do not automatically open downloaded files.

## Assignment and deadline requirements

- `tasks` and `assignments` are aliases for the same Moodle Assignment list.
  Support filtering by a positive course ID.
- Show IDs, courses, titles, due dates where present, and reliable submission status.
- Label unavailable submission status as unknown. Missing optional submission access
  must not hide an otherwise accessible assignment.
- Use team submission status for group assignments and apply returned personal
  extensions before filtering deadlines.
- Keep assignments without deadlines in the general assignment list. Keep submitted
  assignments visible; a past deadline does not imply an unsubmitted assignment.
- `upcoming` requires a positive whole-number day count. Include deadlines from the
  current instant through the end of that many elapsed 24-hour periods, including
  both boundaries, sorted by deadline. Older deadlines remain in the general list.
- Present dates in the local time zone with an explicit UTC offset and zone name.

## Quiz requirements

- List quizzes and learning checks separately from Moodle Assignment activities.
  Support the same positive course-ID filter.
- Show quiz and course IDs and available opening, closing, and time-limit metadata.
- Label missing timing as unavailable; only explicit zero values mean no restriction.
- Keep quiz closing times separate from Assignment submission deadlines. `upcoming`
  covers Assignments rather than quiz closing times.
- Do not start, answer, finish, or fetch questions from quiz attempts.
- Leave other activity types accessible through the general course overview.

## Agenda requirements

- Combine assignment deadlines, quiz openings, and quiz closing times in a
  chronological view with explicit event types.
- Default to seven elapsed days and accept 1–366 days. Apply personal assignment
  extensions and report incomplete source access or unavailable quiz timing.

## Presentation and failure requirements

- Provide German and English messages, with English fallback for other system locales.
  `--lang de|en` overrides the language for the whole process and interactive session.
- Keep command names and original course content unchanged across languages.
- Explain supported commands through `--help`. Validate IDs, day counts, file numbers,
  quoting, and argument combinations before performing the corresponding operation.
- Handle empty results, long names, and missing optional values clearly.
- Report unavailable core API functions, insufficient permissions, expired sessions,
  network failures, and invalid input explicitly rather than returning misleading empty lists.
- Preserve partial-access warnings without displaying unsanitized remote warning text.
  Exposed API functions do not imply access to every course or activity.
- Keep tokens, passwords, callback URLs, and private content out of application logs,
  diagnostic errors, source control, and test fixtures.
- Bound network timeouts and retries. Only explicitly allowlisted read operations
  may retry once after a recoverable transport interruption.

## Technical and quality requirements

Use Kotlin/JVM, one Gradle application module, constructor injection, and a committed
Gradle Wrapper. Application/domain code must remain independent of Retrofit, DTOs,
files, and terminal output. Repository interfaces describe replaceable boundaries.
See [Architecture](ARCHITECTURE.md) and [Dependency decisions](DEPENDENCIES.md).

Tests must cover filtering, submission status, authentication, API error mapping,
and safe download paths without requiring a real Moodle account. Native integration
tests use isolated invented credentials and must run on their target platform.

Build and format through the Gradle Wrapper. Style checks belong to the build;
formatting is an explicit action. Keep exact toolchain, library, and plugin versions
in build configuration. See [Development](DEVELOPMENT.md) for the workflow.

Distribute an executable JAR containing Kotlin and runtime dependencies, including
native resources and license notices. Java remains an external prerequisite.

## Local Codex integration

Expose a local stdio MCP server with an explicit allowlist of study queries that
reuse the existing services and saved native login. Do not expose login, token
retrieval, submissions, quiz attempts, or arbitrary API/URL tools.
MCP supports individual and batch file downloads into the local user’s
Downloads/Moodle folder without overwriting files. Successful results include the
saved path and byte count.
Bound and paginate results, omit resource URLs and identity metadata, and return
sanitized errors. Treat returned course content as untrusted data in the bundled
Codex skill. Document that retrieved content enters the Codex conversation.
See [MCP](MCP.md) for setup and the supported tool contract.

## Future extensions

- `setup <url>` to validate and persist another Moodle instance's base URL.
- A native credential-store adapter for Linux.
- Release packaging with a bundled Java runtime.

These are extension ideas, not available commands or promised delivery dates.

## Out of scope

- Assignment submissions, uploads, and other server-side content changes.
- Teacher or administrator workflows.
- Answering quizzes or exams automatically.
- Overriding institutional access restrictions or supporting every Moodle/authentication setup.
- Importing an existing browser profile, cookies, or password-manager data.
- Entering institutional passwords or automating MFA.
- A graphical interface, hosting, and LLM API calls inside the application.
