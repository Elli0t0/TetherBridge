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
