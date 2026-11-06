# Authentication

[Project overview](../README.md) · [Sign-in guide](GETTING_STARTED.md) · [Development](DEVELOPMENT.md)

## Automatic browser login

`moodle login` uses an installed Chrome/Chromium on macOS, Linux, or Windows. It creates a
separate temporary profile with owner-only directory permissions and a Chrome
off-the-record browser context. The normal browser profile is never opened or
imported. The user completes SWITCH authentication, including MFA, in that window.

Chrome communicates with the CLI through inherited process pipes. No remote
debugging TCP port or callback HTTP server is opened. The final page's app link is
captured without registering or replacing any system URL handler, including
`mmzhaw`. Browser downloads are denied for this context. Browser logging and crash
reporting are disabled through launch flags; stderr is discarded rather than
forwarding browser diagnostics into application logs.

The temporary browser and profile are closed and removed on completion, errors,
timeout, and normal process shutdown. Cleanup failures are reported with a generic
message. Force-killing the application or an OS crash can interrupt cleanup; the
CLI does not promise secure erasure of OS-managed memory or temporary storage.

## What is read

The capture script checks the top-level origin, launch-page path, and current
passport before returning only the `launchapp` anchor's `href`. It does not read
password fields, cookies, page HTML, or other browser tabs. It prevents the final
app link from being opened by the system's registered app. It does not fill forms
or automate MFA. Protocol events and error payloads are not logged.

## Token validation and storage

- A cryptographically random passport binds each callback to one login attempt.
- The callback's site/passport hash must match before extracting the token. MD5 is
  used only because Moodle defines that protocol field; it is not password hashing.
- Attempts are single-use and valid for fifteen minutes. Automatic browser waiting
  is limited to ten minutes, and individual browser commands also have timeouts.
- The extracted token is checked against Moodle using `core_webservice_get_site_info`.
  The returned site must match the configured instance.
- API credentials are sent in a POST body over HTTPS. Redirects and OkHttp's
  automatic connection retries are disabled. Explicitly allowlisted read-only REST
  operations may retry once after a transport interruption. Unknown or mutating
  functions, TLS failures, DNS failures, and interrupted or timed-out calls are not
  retried. A second failure is reported without an unbounded loop.
- Callback URLs and tokens are not printed, written to configuration, placed in
  process arguments, or copied to the clipboard by automatic login.
- On macOS, a verified token is stored in Keychain; Windows uses Credential Manager. `--no-store` keeps
  the entire process memory-only without reading or changing saved entries.
- `exit` clears process-local references but keeps a saved login. `logout` also
  deletes the application's saved token for this site; it does not revoke the
  Moodle token on the server.

The user still signs in to Moodle and SWITCH. The separate browser context has its
own session, so it may request authentication even when the normal browser is logged in.

## Native credential storage

`TokenStore` is an application boundary; its macOS adapter uses JNA to call Apple's
`SecItemAdd`, `SecItemUpdate`, `SecItemCopyMatching`, and `SecItemDelete` APIs.
The generic-password service is `moodle-cli`, with the configured Moodle site as
its account key. Only this entry is queried; the application does not enumerate
other passwords or read institutional login credentials. No shell command is used.

Tokens are validated against the configured site before saving, and restored
entries are checked against Moodle before private feature requests. Missing entries
mean signed out; permission or storage failures are distinct errors and never
trigger an unencrypted fallback. Native objects are released and temporary byte
buffers are cleared, but JVM strings and OS memory are not guaranteed to be erased.
The OS may require the user to unlock Keychain or approve access. The CLI never
enters the user's Keychain password automatically.

The composition root selects the platform adapter. Linux uses `--no-store` until an
OS-backed implementation exists. The credential store protects storage using the user's OS
security model; an unsigned CLI using a shared JDK does not isolate credentials
from malicious code running under that same user or JVM identity. Signed application
packaging and a bundled runtime are a separate deployment decision.

## Windows native integration

Windows Credential Manager stores a generic credential with target
`moodle-cli:<site-url>` for the current Windows user. `CRED_PERSIST_LOCAL_MACHINE`
retains it across that user's logons without roaming it to other machines. Only this
application's exact target is read or deleted. Only `ERROR_NOT_FOUND` means signed
out; access errors and invalid blobs remain errors. Native buffers are cleared and
released. Tokens never appear in process arguments or ordinary configuration files.

Automatic login starts Chrome directly with `CreateProcessW`, without a command
shell. Only the two protocol pipe handles and a null-device handle for discarded standard
input/output/error are inherited. The suspended process is
assigned to a kill-on-close job before it starts, so cleanup owns its child processes.
The profile directory is created with a protected DACL granting only the current
process user full access, including inherited access to its files. Parent-directory
permissions are not inherited. A browser log-file environment override is removed.
The existing callback validation and private Chrome context are shared by all platforms.

Windows-native tests require Windows and do not use a real Moodle account:

```powershell
$env:MOODLE_WINDOWS_CREDENTIAL_TESTS = "true"
$env:MOODLE_CHROME_TESTS = "true"
.\gradlew.bat check
```

The credential test creates a randomly named entry containing invented values and
removes it afterwards. Browser tests use a local fixture, verify profile permissions,
and check cleanup after success, timeout, and start failure. These native paths must
be exercised on Windows before claiming Windows runtime verification.

## Local MCP queries

The optional [Codex plugin](MCP.md) reuses the saved login and existing services.
Authentication stays in a separate terminal flow; MCP tools do not request tokens,
open browser login, or serialize credentials and protected file URLs. Calls within
one server process are serialized. Expired-token cleanup follows the same rules as
the CLI. Course text is treated as untrusted data, and returned material becomes
part of the Codex conversation; local stdio does not imply local-only model processing.

## Manual alternative

`moodle login --manual` opens the normal browser and accepts the app link through
hidden terminal input. It applies the same callback and API validation. Copying the
link manually places it on the clipboard; the automatic flow avoids this step.

## Verification

Normal tests cover credential persistence, per-site validation, expiration cleanup,
logout, memory-only isolation, bounded read retries, callback validation, replay and expiry, secret redaction,
POST-body transport, redirect rejection, and browser-pipe errors and timeouts.
Optional tests launch real headless Chrome against a local fixture with invented
tokens and check callback capture, page restrictions, private profile permissions,
and cleanup. They do not sign in to Moodle or SWITCH:

```sh
MOODLE_CHROME_TESTS=true ./gradlew test
```

Native Keychain tests are opt-in and create an isolated, randomly named test entry
containing only invented token values, then remove it:

```sh
MOODLE_KEYCHAIN_TESTS=true ./gradlew test
```

## References

- [Moodle mobile launch implementation](https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/admin/tool/mobile/launch.php)
- [Chrome profile isolation for remote debugging](https://developer.chrome.com/blog/remote-debugging-port)
- [Chrome DevTools browser contexts](https://chromedevtools.github.io/devtools-protocol/tot/Target/)

- [Java Native Access](https://github.com/java-native-access/jna)
- [Apple Keychain Services](https://developer.apple.com/documentation/security/keychain-services)

- [Windows Credential Manager](https://learn.microsoft.com/windows/win32/api/wincred/nf-wincred-credwritew)
- [Windows process handle inheritance](https://learn.microsoft.com/windows/win32/api/processthreadsapi/nf-processthreadsapi-updateprocthreadattribute)
- [Windows job objects](https://learn.microsoft.com/windows/win32/procthread/job-objects)
