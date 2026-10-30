# Moodle CLI

A Kotlin command-line project for accessing Moodle courses, assignments, deadlines,
and course materials. Built as a practical study tool and a Kotlin learning project.

## Scope

The target is a local, read-only tool for one user and one Moodle instance:

- View enrolled courses and assignments.
- Filter assignments by course and deadline.
- Display submission status when available.
- Download course materials.

These are project goals, not a list of completed features. See `--help` for the
command interface. API access depends on the target Moodle instance and permissions.

## Build and run

Use a JDK matching the toolchain declared in [build.gradle.kts](build.gradle.kts).
Open this folder as a Gradle project in IntelliJ IDEA, or use the Gradle Wrapper:

```sh
./gradlew build
./gradlew run --args="--help"
./gradlew test
```

Formatting follows `.editorconfig` and is checked as part of the build:

```sh
./gradlew ktlintFormat  # Apply Kotlin formatting
./gradlew ktlintCheck   # Check Kotlin formatting without modifying files
```

On Windows, use `gradlew.bat`. To generate a local launcher:

```sh
./gradlew installDist
./build/install/moodle/bin/moodle --help
```

## Check API access and sign in

The CLI currently targets `https://moodle.zhaw.ch/`. Use the installed launcher in a
real terminal for hidden credential input:

```sh
./gradlew installDist
./build/install/moodle/bin/moodle health
./build/install/moodle/bin/moodle login
```

`health` checks the public Web Service configuration. `login` opens a separate
private Chrome/Chromium window on macOS or Linux. Complete the normal SWITCH login;
the CLI receives the final app link automatically and closes that browser. Your
normal browser profiles and the official ZHAW app link registration are not changed.

For another browser, or if automatic login is unavailable:

```sh
./build/install/moodle/bin/moodle login --manual
```

Complete the normal login, then right-click the app launch link on the final Moodle
page and choose Copy Link. Paste it into the terminal's hidden prompt, not into chat
or a shell command. Use the link from the current login attempt.

Both paths validate the callback against the current attempt and verify the token
with `core_webservice_get_site_info`. The in-memory session accepts `health`,
`logout`, and `exit`. Credentials are not saved between processes. A callback is
single-use and expires after fifteen minutes; automatic browser waiting stops after
ten minutes. Close the separate browser window or press Ctrl+C to cancel.

Function exposure does not guarantee access to every course or activity. Course
listing and assignment requests remain separate implementation milestones.
See [Authentication](Docs/AUTHENTICATION.md) for the security boundaries and browser requirements.

## Structure

```text
src/main/kotlin/moodle/
├── Main.kt          # Dependency wiring and entry point
├── cli/             # Commands and terminal output
├── application/     # Workflows and filtering
├── domain/          # Models and repository contracts
├── infrastructure/  # Retrofit API and repository adapter
└── config/          # Local connection settings

src/test/kotlin/moodle/
```

The service depends on a repository interface. The Retrofit adapter implements it.
Dependencies are passed through constructors; tests can use a fake repository and
fixed clock. Library versions are defined in the build files.

Never commit tokens or passwords. A file being ignored by Git does not provide
secure credential storage.

## Documentation

- [Specification](Docs/SPEC.md): requirements and scope.
- [Architecture](Docs/ARCHITECTURE.md): dependency boundaries and Kotlin conventions.
- [Dependencies](Docs/DEPENDENCIES.md): library choices and rationale.

This is an independent project and is not affiliated with Moodle or Moodle HQ.
