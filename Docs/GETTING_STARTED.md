# Getting started

[Project overview](../README.md) · [Command guide](USAGE.md) · [Development](DEVELOPMENT.md)

## Prerequisites

- A Moodle account for `https://moodle.zhaw.ch/`, with mobile Web Service access.
- Java matching `javaToolchainVersion` in [gradle.properties](../gradle.properties).
  Check the installed runtime with `java -version`.
- A real terminal for hidden input: Terminal on macOS, PowerShell or Command Prompt
  on Windows, or a terminal on Linux. An IDE's output pane may not provide a console.
- An installed Chrome/Chromium for automatic login. On Windows, discovery searches
  standard Google Chrome installations under Program Files and Local AppData.
  Manual login works with your normal browser instead.

The application does not include Java. A JAR contains Kotlin and the application
libraries, so recipients do not need a separate Kotlin or Gradle installation.
It contains no account credentials or downloaded course materials.

## Choose how to run the CLI

### Standalone JAR

Place `moodle.jar` in a directory of your choice and open a terminal there:

```sh
java -jar moodle.jar --help
java -jar moodle.jar --lang de login
```

These commands also work in Windows PowerShell. Start the JAR from a terminal;
double-clicking it does not provide the interactive command prompt.

To build the JAR yourself, follow [Standalone JAR](DEVELOPMENT.md#standalone-jar).

### Generated launcher

From a source checkout, create the launcher with the Gradle Wrapper:

```sh
./gradlew installDist
./build/install/moodle/bin/moodle --lang de login
```

Windows PowerShell:

```powershell
.\gradlew.bat installDist
.\build\install\moodle\bin\moodle.bat --lang de login
```

The examples below use `java -jar moodle.jar`. The generated launcher accepts the
same options and commands. Run as your normal OS user rather than as administrator.

## Sign in

```sh
java -jar moodle.jar --lang de login
```

The CLI opens a separate private Chrome window. Complete the normal SWITCH login,
including Microsoft authentication and MFA where required. The CLI captures the
final Moodle app link, verifies the token, and closes its browser window.

This browser has its own temporary profile. It does not import cookies, extensions,
or a password manager from your normal browser profile. Close the separate window
or press Ctrl+C to cancel. Browser waiting is limited to ten minutes.

After signing in, try:

```text
courses
course 1234
assignments
quizzes
upcoming --days 7
exit
```

Replace example IDs with IDs shown by your own course list. See [Usage](USAGE.md)
for the complete command reference and examples.

### Manual login

Use your normal browser when automatic login is unavailable:

```sh
java -jar moodle.jar --lang de login --manual
```

1. Complete SWITCH authentication on the login page opened by the current command.
2. On the final Moodle page, right-click the app launch link and choose **Copy Link**.
3. If the browser asks to open an app, cancel that prompt.
4. Paste the copied app link into the CLI's hidden prompt. Enter `cancel` to stop.

Copy the page's app link, not the `https://` address from the browser address bar.
An app link may begin with `mmzhaw://token=`. Use the link from this exact login
attempt; links from earlier attempts are rejected. Do not paste callback links
into chat, issues, shell commands, or screenshots. Manual copying uses the clipboard;
automatic login avoids that step.

## Saved login, exit, and logout

By default, macOS stores a verified token in Keychain; Windows uses Credential
Manager. Subsequent commands restore and verify the saved token automatically:

```sh
java -jar moodle.jar --lang de courses
java -jar moodle.jar --lang de upcoming --days 7
java -jar moodle.jar --lang de logout
```

- `exit` leaves the interactive prompt and keeps the saved login.
- `logout` clears process state and removes this application's saved token for the
  configured site. It does not revoke the token on Moodle's server.
- An expired or rejected token is removed. Temporary network failures keep it.
- A credential-store failure is reported explicitly; credentials are never silently
  written to an ordinary configuration file.

For a session held only in process memory:

```sh
java -jar moodle.jar --lang de --no-store login
```

This mode never reads, writes, or deletes saved credentials. Linux currently
requires it. Continue running study commands inside that login session: a new
process cannot restore a memory-only token.

## Language

German system locales, including Swiss German, select German messages. Other
locales use English. Override the language before the command:

```sh
java -jar moodle.jar --lang de login
java -jar moodle.jar --lang en --help
```

The selection applies to the whole process and interactive session. Command names
and original course content remain unchanged. Global options such as `--lang` and
`--no-store` must precede the command.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Java is missing or rejects the class-file version | Install Java matching the toolchain declared in `gradle.properties`; check `java -version`. |
| Hidden input is unavailable | Start the JAR or generated launcher in a real terminal rather than an IDE output pane. |
| Chrome is not found | Install Chrome in a standard location, or use `login --manual`. |
| Existing cookies or password-manager extensions are absent | Automatic login uses an isolated profile. Use manual login for your normal browser session. |
| Callback is rejected | Copy the app link from the final page opened by the currently running login attempt. Do not reuse an old link. |
| Saved login cannot be read or written | Check OS credential-store access. Use `--no-store login` if you want an explicit memory-only session. |
| A list is partial or an API function is unavailable | Moodle decides access per account and activity. Use `health` and compare with what your account can access in Moodle. |
| A file already exists | Choose a different directory or rename the existing file; downloads never overwrite it. |

`health` reports public Web Service configuration and, when signed in, exposed API
functions. Exposure does not guarantee permission for every activity. If browser
login or mobile Web Services are disabled by the institution, the CLI cannot grant
that access itself.

Never share real callback links, tokens, passwords, or private course content when
reporting a problem. For the detailed security model and native integration tests,
see [Authentication](AUTHENTICATION.md).
