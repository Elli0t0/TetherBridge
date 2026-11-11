# Changelog

## 0.1.0 — Stage 1: Shizuku foundation

- Shizuku API 13.1.5 (`api` + `provider`), `ShizukuProvider` in the manifest,
  permission request flow with result listener.
- `TetherUserService` via AIDL (`ITetherService`: `getInfo()`, `destroy()`),
  bound with `Shizuku.bindUserService`, 15 s timeout with a real reason on failure.
- Screen: Shizuku installed / running / permission, server version, privilege
  uid, Android version, ABI, manufacturer, model; Test / Stop buttons.
- All logging with tag `App`, in-app log view with Copy, every exception
  caught and shown with its real message.
- No tethering, no TUN, no network code yet.
- Fix, same version (CI run #1 red): AGP 8+ ignores `.aidl` files unless
  `buildFeatures { aidl = true }` is set — Kotlin failed with
  "Unresolved reference 'ITetherService'". Added the flag; no other code
  changed for this fix.
- Signing: debug APK now signed with a persistent key
  (`TETHERBRIDGE_*` secrets, or the workflow's one-time key uploaded as
  the `tetherbridge-signing-keystore` artifact) so later updates install
  over this version.
