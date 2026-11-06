# Usage

[Project overview](../README.md) · [Getting started](GETTING_STARTED.md) · [Specification](SPEC.md)

## Command forms

Run commands inside the interactive `moodle>` prompt after `login`, or as standalone
commands when a login has been saved in the OS credential store:

```sh
java -jar moodle.jar --lang de courses
```

Inside the prompt, enter only `courses`, without `java -jar` or global options.
Memory-only sessions must stay in the same process; see [Saved login](GETTING_STARTED.md#saved-login-exit-and-logout).

## Command reference

| Command | Purpose |
| --- | --- |
| `--help` | Show command help. |
| `mcp` | Start the local stdio server for Codex; see [MCP](MCP.md). |
| `health` | Check public Web Service configuration and available authenticated API functions. |
| `login` | Sign in through the separate private browser and open the interactive prompt. |
| `login --manual` | Sign in through your normal browser and paste the app link into hidden input. |
| `logout` | Clear the active session and remove this application's saved login. |
| `courses` | List enrolled courses with IDs and names. |
| `course <course-id>` | Show accessible sections, activity descriptions, inline text, and numbered files. |
| `read <course-id> <module-id>` | Read an activity's text, including the content of Moodle Page resources. |
| `download <course-id> <module-id> <file-number> [directory]` | Save one listed file. |
| `assignments [--course <course-id>]` | List assignments, due dates, and available submission status. |
| `tasks [--course <course-id>]` | Alias of `assignments`. |
| `quizzes [--course <course-id>]` | List quizzes and available schedule/time-limit metadata. |
| `upcoming --days <days>` | List assignments due from now through the requested number of elapsed days. |
| `exit` | Leave the interactive prompt, retaining a saved login. Only available within the session. |

IDs and file numbers must be positive integers. Day counts must be positive whole
numbers. `setup <url>` is a planned extension, not an available command; the Moodle
instance is currently configured in the application.

## Courses and text

```text
courses
course 1234
read 1234 5678
```

Use a course ID from `courses`. The course overview lists sections, accessible
activities, descriptions, inline text, and numbered files. Activity IDs identify
course modules; assignment IDs and quiz IDs from their separate lists are not
interchangeable with module IDs.

`read` displays an activity's text and fetches the full content of Moodle Page
resources where available. HTML is parsed locally into plain text. Scripts and
terminal control characters are removed; embedded resources are not fetched by
the HTML renderer. Other activity types may appear in the overview without having
a dedicated reader. The CLI does not retrieve quiz attempt questions.

## Download a file

```text
download 1234 5678 1
download 1234 5678 1 "./My Materials"
```

Choose the course ID, activity/module ID, and file number shown by `course`.
File numbers start at one within each activity. Downloads go to `./downloads`
relative to the terminal's current directory unless another directory is supplied.
Quote directory paths containing spaces. Windows paths may also be quoted:

```text
download 1234 5678 1 "C:\Users\Example\Documents\Course Materials"
```

Existing files and symbolic links are never overwritten. There is no overwrite
option or bulk-download command. Unsafe filenames, including reserved Windows
device names, are rejected. Files are not opened automatically.

A download is limited to 512 MiB; reading a text page is limited to 2 MiB. File
requests are restricted to the configured Moodle file endpoint, do not follow
redirects, and carry the token in the POST body. Moodle must allow file downloads
through its mobile Web Service. See [Authentication](AUTHENTICATION.md) for the
credential and transport boundaries.

## Assignments and upcoming deadlines

```text
assignments
assignments --course 1234
tasks
upcoming --days 7
```

Assignments show their IDs, course names, deadlines when present, and available
submission status. The course filter accepts a course ID from `courses`.
Assignments without deadlines remain visible in `assignments`/`tasks`; the
`upcoming` view contains only deadlines within its window.

The upcoming window includes both the current instant and the end of the selected
number of elapsed 24-hour periods. It is not a count of calendar dates. Results are
sorted by deadline. Dates use the local time zone and include its name and UTC offset.

Submitted assignments are still listed. A passed deadline is labelled separately
and does not imply that the assignment is unsubmitted. Where available, the CLI
uses group submission status for group assignments and applies returned personal
extensions before deadline filtering. Missing or inaccessible submission details
are marked unknown; check Moodle directly if your extension cannot be retrieved.

Partial access is indicated explicitly. The API can report activities hidden from
your account, such as activities subject to group or availability restrictions.
A warning does not mean that all course access or your login has failed. Network
and authentication failures are reported as errors rather than empty lists.

## Quizzes and learning checks

```text
quizzes
quizzes --course 1234
```

Quizzes are separate from Moodle Assignment activities. Their list shows quiz ID,
course ID, available opening and closing times, and time limit per attempt.
Missing timing is labelled unavailable. Explicit zero values returned by Moodle
mean no corresponding restriction.

`upcoming` covers Assignment due dates, not quiz closing times. Quizzes are read as
metadata only: listing them does not start, answer, finish, or fetch questions from
an attempt. Other activity types remain visible through `course <course-id>`.

## API availability

| Feature | Moodle Web Service functions |
| --- | --- |
| Verify identity and exposed functions | `core_webservice_get_site_info` |
| Enrolled courses | `core_enrol_get_users_courses` |
| Course contents | `core_course_get_contents` |
| Assignments | `mod_assign_get_assignments` |
| Optional submission details and extensions | `mod_assign_get_submission_status` |
| Quiz metadata | `mod_quiz_get_quizzes_by_courses` |

Function exposure and activity permissions are separate checks. Missing optional
submission details do not hide otherwise accessible assignments. Unsupported core
features are reported explicitly. The application cannot override institutional
permissions or make inaccessible activities available.
