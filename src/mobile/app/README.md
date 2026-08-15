# app/

Android client. Kotlin and Jetpack Compose. Application id `dev.lantern.app`.

**Android is the only Phase 0/1 target.** iOS follows in Phase 4 against the
same `core` module and contract tests (spec §16.1, §19). Framework decision
in [docs/mobile-framework-kotlin.md](../../../docs/mobile-framework-kotlin.md).

## What Phase 0 ships

| Piece | Notes |
|---|---|
| Compose Home | Lifecycle refresh, explicit `lastSeenUtc`, durable send queue, bounded in-flight watch |
| Theme | Colors loaded from [`docs/brand-tokens.json`](../../../docs/brand-tokens.json) |
| `SecureStorage` | Keystore-backed `EncryptedSharedPreferences`; MSAL cache integration remains a release gate |
| `AuthCallbackHandler` | Honest scaffold; interactive MSAL redirect / PKCE handling remains human-gated |
| `BackgroundScheduler` / `NotificationService` | Honest stubs: they record the request, they do not pretend work ran |
| `IdentityService` | Honest scaffold; no Graph token is acquired in the Phase 0 debug build |
| Local mailbox | Filesystem App Folder under the app files dir (harness). Graph is human-gated. |
| Sync index | SQLite metadata/cursors with payload bytes in Keystore-backed storage |
| Runtime metrics | Content-free operation, duration, retry, cache-count, and sync-age records in Logcat |

```bash
( cd src/mobile && ./gradlew :app:assembleDebug :app:testDebugUnitTest )
```

`copyBrandTokens` copies [`docs/brand-tokens.json`](../../../docs/brand-tokens.json)
into generated `assets/` and `test/resources/` (gitignored).
`BrandTokensTest` and `BrandThemeSourceTest` assert those copies match the
docs file. `HomeViewModelTest` asserts a stale `lastSeenUtc` maps the pane
to offline. No emulator is required.

`:app` is included only when `ANDROID_HOME` / `ANDROID_SDK_ROOT` is set or
`src/mobile/local.properties` exists, so `./gradlew :core:build` still
works on machines without the Android SDK.

## Rules for this module

- **Android-only APIs live here, never in `:core`.** The `core` module is pure
  Kotlin/JVM. That boundary is what makes iOS an implementation phase rather
  than a rewrite.
- Implement the `dev.lantern.core.platform` interfaces here.
  - `SecureStorage` uses Keystore-backed encrypted storage (holds the MSAL
    token cache).
  - `BackgroundScheduler` uses WorkManager status/response checks within
    Doze/standby constraints (Phase 1 attaches the worker).
  - `NotificationService` covers completion, failure, and desktop-attention
    notifications. Heartbeat digests only (spec §9.2).
  - `AuthCallbackHandler` handles MSAL redirect/browser flow (auth code and
    PKCE). No client secret in the APK.
  - `ExternalAppLauncher` opens artifacts in Microsoft 365 apps via OneDrive
    links.
- Implement `dev.lantern.core.application.IdentityService` over MSAL
  Android with the broker. Scopes come from `IdentityService.MVP_SCOPES`.
  Never broaden them ad hoc (spec §10.2).
- Screens (spec §9) are Home, Activity, Chats, Artifacts, Automations, Usage,
  and Settings. Home must always show explicit freshness (`lastSeenUtc`).
  Never show "online" without evidence.
- Foreground sync uses the in-flight watch mode of spec §16.2. Poll an
  in-flight command's response on a short interval, back off exponentially,
  and stop on terminal status or when backgrounded.
- Exit bar for Phase 1. A signed release build ready for Google Play
  internal/closed testing, plus the device-level acceptance and security tests
  of spec §18.
