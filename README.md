# TetherBridge

Share the phone's hotspot / USB tethering traffic through a Shizuku-privileged
test network — without root.

## What this is, in simple terms

Normally an app cannot touch the phone's low-level networking. Shizuku is a
helper app that runs a small privileged process (as the `shell` user, uid 2000)
and lets this app run its own code inside that process. This app's plan:

- **Stage 1 (this version):** prove the Shizuku connection works. Show
  whether Shizuku is installed, running, and allowed, and run one tiny test
  inside the privileged process.
- **Stage 2:** create a virtual network interface (TUN) inside the privileged
  process and register it as a test network.
- **Stage 3:** tell Android to prefer that test network for hotspot/USB
  tethering (Android 13+).
- **Stage 4:** forward packets between the virtual interface and the real
  network. The packet-moving code will be written in **Rust** (not Go):
  Rust gives us memory safety without a garbage collector (so no pauses in
  the middle of packet forwarding), a small static binary for both
  arm64-v8a and armeabi-v7a via cargo-ndk, and a tiny JNI surface —
  `start(tunFd)`, `stop()`, `stats()` — with errors logged back to Kotlin.
  A userspace TCP/IP stack crate (smoltcp) will be used only if it earns its
  place; otherwise plain packet parsing wins, because less code means fewer
  surprises on a phone. That choice is re-checked when Stage 4 starts.

## Stage 1 — what it does

1. **ShizukuProvider** (a ContentProvider declared in
   `AndroidManifest.xml`) is how Shizuku hands this app its Binder.
   No `<service>` entry is declared for the UserService on purpose — the
   Shizuku server starts it from the class name, and the official Shizuku
   demo manifest declares no `<service>` either.
2. The screen shows: Shizuku installed / running / permission granted,
   server version, privilege uid (0 = root, 2000 = shell), Android version,
   ABI, manufacturer, model.
3. **Request permission** asks Shizuku for access (a Shizuku dialog).
   The result comes back through `OnRequestPermissionResultListener`.
4. **Test UserService** binds `TetherUserService` (an AIDL service,
   `ITetherService.aidl`) in the privileged process and calls `getInfo()`,
   which returns the uid and device info from inside that process.
   The bind waits at most 15 seconds; a timeout is reported as a timeout
   with likely causes, never as a generic error.
5. **Stop UserService** sends `destroy()` (the service exits its own
   process) and then unbinds. Unbinding alone does NOT kill the service
   process — that is Shizuku behaviour, not a bug.
6. Everything is logged with tag **`App`**, and the screen shows the same
   log lines (with a Copy button) so they can be sent back without logcat.

## How to test Stage 1

1. Install Shizuku, start it (wireless debugging on Android 11+, or adb
   from a computer), and leave it running.
2. Install this debug APK.
3. Open TetherBridge. It should show Installed: yes, Running: yes.
4. Tap **Request permission**, allow it in the Shizuku dialog.
5. Tap **Test UserService**. It should show `Test result: OK` and a line
   like `uid=2000 (shell) | sdk=... | android=... | manufacturer=...`.
6. Tap **Stop UserService**.

## Debugging — which logcat lines to send back

If anything is not OK, send the output of:

```
adb logcat -d -s App
```

Or tap **Copy log** in the app and paste that text. The most useful lines
are the ones starting with `E`, plus the full `testService` block:

- `readStatus: ...` — what the app saw (installed / pingBinder /
  checkSelfPermission / getVersion / getUid).
- `requestPermission: ...` and `onRequestPermissionResult: ...` — the
  permission dialog result.
- `testService: starting` … `bindUserService() returned, waiting up to
  15000ms` … `onServiceConnected ...` — where the bind stopped.
- `TetherUserService: ... constructor` — proves the privileged process
  started and which constructor Shizuku used.
- `TetherUserService.getInfo: ...` — the answer from inside the
  privileged process.
- Any line with `FAILED` or `TIMEOUT` — that is the real error, copied
  verbatim into the screen's Test result box too.

Also say: phone model, Android version, Shizuku version, and whether
Shizuku was started by wireless debugging, adb, or root.

## What is NOT in Stage 1 (on purpose)

No TUN, no test network, no tethering changes, no packet forwarding, no
foreground service, no Quick Settings tile, no network permission at all.
If a hidden API is blocked on a device, later stages will say so in the UI
instead of guessing.

## Build

- Kotlin + Jetpack Compose + Material 3, minSdk 30, compile/targetSdk 36.
- Shizuku API 13.1.5 (`dev.rikka.shizuku:api` + `:provider`, Maven Central).
- AGP 9.3.1, Kotlin 2.2.10, Gradle 9.5.0 (CI), JDK 17.
- ABIs: arm64-v8a + armeabi-v7a.
- Every push to `main` builds a debug APK (GitHub Actions,
  `.github/workflows/android-build.yml`, artifact `TetherBridge-debug`).
- The debug APK is signed with a persistent key (not the throwaway debug
  key): the workflow uses `TETHERBRIDGE_*` repo secrets when they exist,
  otherwise it generates a one-time key and uploads it as the
  `tetherbridge-signing-keystore` artifact (keystore + password file).
  Save that artifact as the four `TETHERBRIDGE_*` secrets before the next
  update, then delete the artifact — every later APK then installs over
  the previous one. Never commit a keystore or a password to the repo.
