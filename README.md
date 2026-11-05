# Moodle CLI

A Kotlin command-line project for accessing Moodle courses, assignments, deadlines,
and course materials. Built as a practical study tool and a Kotlin learning project.

This is an unofficial student project. It is not affiliated with, endorsed by, or
supported by ZHAW or Moodle. Each user signs in with their own account; access is
limited to the permissions granted by their Moodle instance.

## Scope

The target is a local, read-only tool for one user and one Moodle instance:

- View enrolled courses and assignments.
- Filter assignments by course and deadline.
- Display submission status when available.
- Download course materials.

These are project goals, not a list of completed features. See `--help` for the
command interface. API access depends on the target Moodle instance and permissions.

## Build and run

Use a JDK matching the toolchain declared in [gradle.properties](gradle.properties).
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

For Windows PowerShell, use the generated batch launcher:

```powershell
.\gradlew.bat installDist
.\build\install\moodle\bin\moodle.bat --lang de login
.\build\install\moodle\bin\moodle.bat --lang de courses
```

Automatic login discovers Google Chrome installed system-wide or for the current
Windows user. Run the CLI as your normal user in a terminal. Java is still required;
release packaging with a bundled runtime is a separate step.

## Standalone JAR

Build a single executable JAR containing the application, Kotlin runtime, and all
runtime dependencies:

```sh
./gradlew shadowJar
java -jar build/libs/moodle.jar --lang de login
```

On Windows PowerShell:

```powershell
.\gradlew.bat shadowJar
java -jar .\build\libs\moodle.jar --lang de login
```

Share `build/libs/moodle.jar`; recipients do not need Kotlin, Gradle, or the source
repository. They still need Java matching the toolchain in `gradle.properties` and
Chrome for automatic login. Start the CLI from a terminal so hidden login input
is available. Native access is enabled by the executable JAR manifest. The same
JAR contains the macOS and Windows adapters; Linux requires `--no-store`.
`build` also creates this JAR. This artifact does not contain a Java runtime or
any local credentials, downloaded course files, or browser profiles.

## Language

Messages and help follow the system language: German for German locales (including
Swiss German), English otherwise. Override the language before the command:

```sh
./build/install/moodle/bin/moodle --lang de login
./build/install/moodle/bin/moodle --lang en --help
```

The selected language applies to the entire process and interactive login session. Command names
such as `courses`, `read`, `download`, and `cancel` remain unchanged. Course content
is displayed in its original language. Translations live in UTF-8 resource files
under `src/main/resources/i18n`; English is the fallback language.

## Check API access and sign in

The CLI currently targets `https://moodle.zhaw.ch/`. Use the installed launcher in a
real terminal for hidden credential input:

```sh
./gradlew installDist
./build/install/moodle/bin/moodle health
./build/install/moodle/bin/moodle login
```

`health` checks the public Web Service configuration. `login` opens a separate
private Chrome/Chromium window on macOS, Linux, or Windows. Complete the normal SWITCH login;
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
with `core_webservice_get_site_info`. The in-memory session accepts `courses`,
`course`, `read`, `download`, `assignments` (or `tasks`), `quizzes`, `upcoming`, `health`, `logout`, and `exit`.
On macOS and Windows, a verified login is saved in the OS credential store by default. A callback is
single-use and expires after fifteen minutes; automatic browser waiting stops after
ten minutes. Close the separate browser window or press Ctrl+C to cancel.

Function exposure does not guarantee access to every course or activity.
See [Authentication](Docs/AUTHENTICATION.md) for the security boundaries and browser requirements.

## Saved login and logout

`login` saves the validated Moodle token in macOS Keychain or Windows Credential
Manager. Later commands restore and validate that token before retrieving private Moodle data:

```sh
./build/install/moodle/bin/moodle --lang de courses
./build/install/moodle/bin/moodle --lang de quizzes --course 29614
./build/install/moodle/bin/moodle --lang de upcoming --days 7
./build/install/moodle/bin/moodle --lang de logout
```

`exit` closes the interactive process and keeps the saved login. `logout` clears
process-local state and deletes this application's saved entry for the configured
site. It does not revoke the token in Moodle. An expired or rejected saved token is
removed; a temporary network error keeps it. Denied or unavailable credential-store access
is reported without writing credentials to ordinary files.

For an in-memory session, put `--no-store` before the command:

```sh
./build/install/moodle/bin/moodle --lang de --no-store login
```

This mode never reads, writes, or deletes saved credentials. Linux currently requires
this mode because its native credential-store adapter is not implemented.
Credential storage uses native OS APIs directly through JNA, without placing the
token in shell commands, process arguments, or clipboard contents. A shared JDK
identity does not provide isolation from arbitrary code running as the same user;
see [Authentication](Docs/AUTHENTICATION.md) for the boundary.

## Browse course materials

After signing in, run these commands at the `moodle>` prompt (replace example IDs
with those shown in your course list):

```text
courses
course 1234
read 1234 5678
download 1234 5678 1
download 1234 5678 1 "./My Materials"
```

`course` displays sections, descriptions, inline text, and numbered files for each
activity. `read` displays the text of an activity, including the full HTML content
of Moodle Page resources. HTML is parsed locally into plain text; scripts and
terminal control characters are removed. Complex activities are listed but do not
have a dedicated reader yet. These commands work in the interactive session or as
standalone launcher commands after a login has been saved in the OS credential store.

`download` selects a file by course ID, activity ID, and the file number displayed
by `course`. It saves into `./downloads` by default, or the quoted directory you
provide. Existing files are never overwritten. Unsafe remote filenames and downloads
outside the configured Moodle file endpoint are refused. Downloads are limited to
512 MiB per file; reading a text page is limited to 2 MiB. Redirects are not followed,
and the token is sent in the POST body rather than appended to a URL. Moodle must
enable file downloads for the mobile Web Service. Files are not opened automatically.

## Assignments and deadlines

Within the interactive login session (or as launcher commands after a saved login):

```text
assignments
assignments --course 1234
quizzes
quizzes --course 1234
upcoming --days 7
```

`assignments` (alias `tasks`) lists accessible Moodle Assignment activities with their IDs, course names,
deadlines, and submission statuses. `--course` accepts a positive course ID from
`courses`. `upcoming` requires a positive whole number of days and includes
deadlines from now through that many 24-hour periods, sorted by deadline.
Assignments without a deadline remain visible in `tasks`. Already submitted
assignments are included; a passed deadline is a separate label and does not mean
an assignment is unsubmitted. Dates use the local timezone and show its UTC offset and zone name.

The CLI uses `mod_assign_get_assignments` and, when exposed by the token,
`mod_assign_get_submission_status`. It reads the current user's submissions,
uses group submission status for group assignments, and applies returned personal
extensions before filtering deadlines. Missing or inaccessible submission details
are marked unknown; check personal extensions in Moodle when details are unavailable.
Partial course or activity access is indicated explicitly. Network and authentication
failures are reported as errors rather than as empty lists. `upcoming` includes
Assignment deadlines; quiz closing times are listed separately by `quizzes`.

`quizzes` lists accessible quizzes and learning checks with their quiz ID, course ID,
opening and closing times, and time limit per attempt. It supports the same positive
`--course <id>` filter. The CLI uses `mod_quiz_get_quizzes_by_courses`; missing API
permissions are reported explicitly. Incomplete timing is labelled unavailable;
explicit zero values mean no corresponding restriction. A quiz's closing time is
not presented as an Assignment submission deadline. This command reads metadata
only and does not start, answer, finish, or retrieve questions from quiz attempts.
Other activity types remain visible through `course <id>`.

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

## License and contributions

The project is licensed under the [MIT License](LICENSE). Dependencies retain their
own licenses; their notices are included in the standalone JAR.

Contributions are welcome through pull requests. The maintainer reviews and decides
which changes to merge. A public repository and its license do not grant direct
write access to this repository; that requires an explicit GitHub permission.

Never include Moodle tokens, app callback links, passwords, personal account data,
or non-public course materials in issues, pull requests, screenshots, or test fixtures.
