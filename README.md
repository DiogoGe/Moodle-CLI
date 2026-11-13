# Moodle CLI

A Kotlin command-line tool for Moodle courses, course materials, assignments, and
quizzes. Built for everyday study and as a Kotlin learning project.

This is an unofficial student project. It is not affiliated with, endorsed by, or
supported by ZHAW or Moodle. Each user signs in with their own account, and Moodle
controls which courses and activities they can access.

## Features

- Browse enrolled courses, sections, descriptions, and text pages.
- Search course titles, activities, and file names across enrolled courses.
- Download selected course files individually or in batches without overwriting existing files.
- List assignments, submission status, and upcoming deadlines.
- List quizzes separately, including available schedule and time-limit information.
- See assignment deadlines and quiz dates together in the agenda.
- Use German or English messages and saved logins on macOS and Windows.
- Query study information from Codex through the optional local MCP plugin.

The CLI reads Moodle data; it does not submit assignments or start quiz attempts.
It currently targets `https://moodle.zhaw.ch/`.

## Quick start

You need Java matching the toolchain in [gradle.properties](gradle.properties),
a real terminal, and Chrome for automatic login. Download `moodle.jar` from
[GitHub Releases](https://github.com/DiogoGe/Moodle-CLI/releases) and run:

```sh
java -jar moodle.jar --lang de login
```

Complete SWITCH authentication in the separate browser window, then enter
`courses` at the `moodle>` prompt. Linux requires `--no-store` before `login`.

See [Getting started](Docs/GETTING_STARTED.md) for setup, Windows instructions,
manual login, and common login problems. To build the JAR from source, see
[Development](Docs/DEVELOPMENT.md#standalone-jar).

## Documentation

| Guide | Contents |
| --- | --- |
| [Getting started](Docs/GETTING_STARTED.md) | Prerequisites, sign-in, saved sessions, and troubleshooting. |
| [Usage](Docs/USAGE.md) | Commands, examples, course materials, assignments, and quizzes. |
| [Development](Docs/DEVELOPMENT.md) | Build, formatting, tests, packaging, and source layout. |
| [Codex plugin / MCP](Docs/MCP.md) | Local plugin setup, query tools, and privacy boundaries. |
| [Authentication](Docs/AUTHENTICATION.md) | Browser isolation, token validation, native storage, and security boundaries. |
| [Specification](Docs/SPEC.md) | Requirements, supported scope, and future extensions. |
| [Architecture](Docs/ARCHITECTURE.md) | Dependency boundaries, class diagram, and Kotlin design conventions. |
| [Dependencies](Docs/DEPENDENCIES.md) | Library and packaging choices. |

## License and contributions

Licensed under the [MIT License](LICENSE). Dependencies retain their own licenses;
the standalone JAR includes dependency notices.

Contributions are welcome through pull requests, which the maintainer reviews
before merging. Public visibility does not grant direct repository write access.
See [Development](Docs/DEVELOPMENT.md#contributing) for contribution guidelines.
