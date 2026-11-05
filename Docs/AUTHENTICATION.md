# Authentication

## Automatic browser login

`moodle login` uses an installed Chrome/Chromium on macOS or Linux. It creates a
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
- API credentials are sent in a POST body over HTTPS. Redirects and automatic
  connection retries are disabled for that client.
- Callback URLs and tokens are not printed, written to configuration, placed in
  process arguments, or copied to the clipboard by automatic login.
- Tokens remain in process memory. Closing the CLI session removes its reference;
  persistent credential storage is a separate feature.

The user still signs in to Moodle and SWITCH. The separate browser context has its
own session, so it may request authentication even when the normal browser is logged in.

## Manual alternative

`moodle login --manual` opens the normal browser and accepts the app link through
hidden terminal input. It applies the same callback and API validation. Copying the
link manually places it on the clipboard; the automatic flow avoids this step.

## Verification

Normal tests cover callback validation, replay and expiry, secret redaction,
POST-body transport, redirect rejection, and browser-pipe errors and timeouts.
Optional tests launch real headless Chrome against a local fixture with invented
tokens and check callback capture, page restrictions, private profile permissions,
and cleanup. They do not sign in to Moodle or SWITCH:

```sh
MOODLE_CHROME_TESTS=true ./gradlew test
```

## References

- [Moodle mobile launch implementation](https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/admin/tool/mobile/launch.php)
- [Chrome profile isolation for remote debugging](https://developer.chrome.com/blog/remote-debugging-port)
- [Chrome DevTools browser contexts](https://chromedevtools.github.io/devtools-protocol/tot/Target/)
