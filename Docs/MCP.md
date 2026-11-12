# Codex plugin and local MCP server

[Project overview](../README.md) · [Getting started](GETTING_STARTED.md) · [Development](DEVELOPMENT.md)

## What it provides

The Moodle plugin connects Codex to the existing Moodle services through a local
MCP server. The server runs as `java -jar moodle.jar mcp` using standard input and
output. It opens no HTTP listener and accepts no remote connections.

| Tool | Purpose |
| --- | --- |
| `health` | Report site configuration and whether a saved login can be used, without returning account identity. |
| `list_courses` | List enrolled courses with IDs and names. |
| `get_course_contents` | List course activities, text previews, and file metadata without file URLs. |
| `read_course_text` | Read a selected activity's plain text, including accessible Moodle Page content. |
| `download_course_file` | Save a selected file to the local user’s Downloads/Moodle folder and return its path and byte count. |
| `list_assignments` | List assignments and available submission status, optionally filtered by course. |
| `upcoming_assignments` | List Assignment deadlines in the next elapsed days. |
| `list_quizzes` | List quiz schedule and time-limit metadata without starting attempts. |

There are no MCP tools for login, logout, assignment submissions, quiz
attempts, arbitrary URLs, or arbitrary Moodle API functions. These boundaries do
not change the existing CLI's separate commands.

## Prerequisites and sign-in

Use Java matching the toolchain in [gradle.properties](../gradle.properties) and a
local Codex client that supports local MCP servers/plugins. Saved authentication
requires macOS Keychain or Windows Credential Manager. A memory-only CLI session
cannot supply credentials to the separate MCP process; authenticated MCP queries
are therefore currently intended for macOS and native Windows execution.

Sign in once through the normal CLI in a real terminal:

```sh
./gradlew shadowJar
java -jar build/libs/moodle.jar --lang de login
```

After completing the browser login, enter `exit` to retain the saved login.
Do not enter `logout`, which removes it. On Windows PowerShell, use
`.\gradlew.bat shadowJar` and `java -jar .\build\libs\moodle.jar --lang de login`.
Codex and Java must run as the same OS user that saved the login. Windows credentials
are not automatically shared with a separate WSL/Linux environment.

The plugin never requests a token in its manifest, configuration, environment, tool
arguments, or conversation. If authentication expires, sign in through the CLI again.

## Build the plugin

```sh
./gradlew prepareCodexPlugin codexPluginZip
```

Windows PowerShell:

```powershell
.\gradlew.bat prepareCodexPlugin codexPluginZip
```

Outputs:

- `build/codex-plugin/`: local marketplace with the plugin, skill, license, and bundled JAR.
- `build/distributions/moodle-codex-plugin.zip`: the same marketplace packaged for sharing.

The source under `plugins/moodle/` is a template. Install the generated bundle:
its manifest version comes from the application version, and it contains the MCP
server JAR. Neither bundle contains credentials, downloads, or browser profiles.
Java remains an external prerequisite.

## Install in Codex

From the repository root, with the Codex CLI available:

```sh
codex plugin marketplace add ./build/codex-plugin
codex plugin add moodle@moodle-local
```

Use the plugin in a new local Codex chat; restart the desktop app if it has not
picked up the new local source. The local marketplace is named **Moodle CLI local
plugins**, and the plugin is **Moodle**. It includes the `moodle-query` skill.

The plugin launches its own JAR copy using `${PLUGIN_ROOT}`, so it does not rely on
a particular repository checkout or working directory. Local installation copies
the plugin into Codex's managed cache. Keep or rebuild the source marketplace before
refreshing it: `./gradlew clean` removes the generated source under `build/`.
When sharing the ZIP, extract it to a persistent directory and register that directory
as the marketplace instead.

Example requests:

- “Show my assignments due in the next seven days.”
- “Find my programming course and summarize its introduction page.”
- “List the quizzes available in this course and their closing times.”

Local plugins are separate from publishing to the public plugin directory. This
project does not submit or publish the plugin automatically. See the official
[plugin packaging guide](https://developers.openai.com/plugins/build/plugins).

### MCP-only alternative

If your Codex client supports MCP configuration but not local plugins, add the
server directly instead. Use an absolute path to a JAR you will keep available:

```sh
codex mcp add moodle -- java -jar /absolute/path/to/moodle.jar --lang en mcp
```

Windows example:

```powershell
codex mcp add moodle -- java -jar "C:\Tools\Moodle\moodle.jar" --lang en mcp
```

Alternatively, add the equivalent entry through Codex MCP settings:

```toml
[mcp_servers.moodle]
command = "java"
args = ["-jar", "/absolute/path/to/moodle.jar", "--lang", "en", "mcp"]
```

Choose either the plugin or the MCP-only entry to avoid duplicate tool connections.
The direct MCP entry provides the same tools but does not install the skill.
See the official [Codex MCP documentation](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

## Query behavior

- `download_course_file` requires positive `course_id`, `module_id`, and one-based
  `file_number` from course contents. It saves into `~/Downloads/Moodle`
  (`%USERPROFILE%\Downloads\Moodle` on Windows), never overwrites files, and uses
  the CLI’s 512 MiB limit and protected download transport. It returns only a local
  path and byte count, not the file contents. No arbitrary URL or destination is accepted.
  Unlike queries, this tool declares local write effects in its MCP annotations.


- List tools accept `offset` and `limit`: the default limit is 25, with a maximum
  of 100. `next_offset` is null at the end. Pagination limits presentation; Moodle
  may still return the underlying list in one API request.
- `get_course_contents` requires `course_id` and paginates activities. Each item
  includes section context and a shortened text preview. It returns up to 20 file
  metadata entries per activity, plus `file_count` and `files_shortened`.
- `read_course_text` requires `course_id` and `module_id`. Its offsets count
  characters rather than items; the default limit is 12000 and the maximum is
  20000. Follow `next_offset` to continue reading.
- Assignment and quiz filters accept an optional `course_id`. `upcoming_assignments`
  requires `days` between 1 and 366. Quiz closing times remain separate from
  Assignment due dates.
- Date/time values are ISO-8601 instants. Null timing is distinguished from an
  unrestricted known schedule by `timing_available`; assignment and quiz lists
  preserve Moodle's `partial` indicator.
- Schemas describe supported arguments; the server also validates types, required
  fields, bounds, and unknown fields before executing queries. IDs must be positive
  JSON integers, not quoted strings.
- Structured responses are limited to 256 KiB. If a result is too large, request a
  smaller `limit`. Errors contain a stable code and a sanitized message rather than
  upstream response bodies or stack traces.

## Privacy and trust boundaries

Tokens stay in the OS credential store and the local JVM. Existing site validation,
HTTPS POST transport, permission checks, bounded read retries, and expired-token
cleanup are shared with the CLI. Up to four tool calls run concurrently within one MCP process;
additional calls suspend until a slot is free. Downloads share this limit, so one
download does not block all queries. Initial login restoration and credential-store
operations remain synchronized. Queued calls are cancellable; active calls signal
thread interruption, but blocking network/native operations may finish only at
their own timeout. This is concurrent execution around synchronous adapters, not
a conversion of every HTTP request to a nonblocking API. Standard output is reserved for MCP;
no interactive browser or prompt is started by a tool call.

The output uses explicit field mapping rather than serializing internal models.
File URLs, raw HTML, tokens, and identity metadata are not part of the response
schema. HTML is converted locally to plain text without executing scripts.

A local MCP server does **not** mean that retrieved course content remains only
on your computer. Tool results become part of the Codex conversation and are
processed according to the Codex account/workspace settings. Request only the
private course material you intend to use with Codex. The server does not contact
an LLM API itself or require an additional OpenAI API key.

Course text is untrusted source material. The bundled skill and server instructions
require treating embedded commands or requests as data, not authority to expose
credentials, run programs, or send content elsewhere. No text sanitizer can prove
that arbitrary course material is free of prompt injection.

## Verification

Normal tests use invented credentials and local fixtures. They cover tool discovery,
stdio initialization and calls, EOF shutdown, argument rejection before requests,
pagination, missing authentication, partial results, and secret/URL/error redaction.
Run `./gradlew check` before packaging. Native login and credential-store tests are
described in [Authentication](AUTHENTICATION.md).
