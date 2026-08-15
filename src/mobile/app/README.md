# app/

Android client. Kotlin and Jetpack Compose.

**Android is the only Phase 0/1 target.** iOS follows in Phase 4 against the
same `core` module and contract tests (spec §16.1, §19). Framework decision
in [docs/mobile-framework-kotlin.md](../../../docs/mobile-framework-kotlin.md).

This directory intentionally contains no generated project yet. The Android
module is bootstrapped at the start of Phase 0 with current Android Studio / AGP
templates so the scaffold matches the toolchain actually in use.

1. In Android Studio, create an "Empty Activity" (Compose) project with
   application id `dev.scoutremote.app`, then move its `app/` module here and
   add `include(":app")` to [`../settings.gradle.kts`](../settings.gradle.kts).
2. Add `implementation(project(":core"))` plus MSAL Android
   (`com.microsoft.identity.client:msal`) once the Entra app registration
   exists (spec §3, and the week-one App Folder scope validation of the latency
   addendum).

## Rules for this module

- **Android-only APIs live here, never in `:core`.** The `core` module is pure
  Kotlin/JVM. That boundary is what makes iOS an implementation phase rather
  than a rewrite.
- Implement the `dev.scoutremote.core.platform` interfaces here.
  - `SecureStorage` uses Keystore-backed encrypted storage (holds the MSAL
    token cache).
  - `BackgroundScheduler` uses WorkManager status/response checks within
    Doze/standby constraints.
  - `NotificationService` covers completion, failure, and desktop-attention
    notifications. Heartbeat digests only (spec §9.2).
  - `AuthCallbackHandler` handles MSAL redirect/browser flow (auth code and
    PKCE). No client secret in the APK.
  - `ExternalAppLauncher` opens artifacts in Microsoft 365 apps via OneDrive
    links.
- Implement `dev.scoutremote.core.application.IdentityService` over MSAL
  Android with the broker. Scopes come from `IdentityService.MVP_SCOPES`.
  Never broaden them ad hoc (spec §10.2).
- Screens (spec §9) are Home, Activity, Chats, Artifacts, Automations, Usage,
  and Settings. Home must always show explicit freshness (`lastSeenUtc`).
  Never show "online" without evidence.
- Foreground sync uses the in-flight watch mode of the latency addendum. Poll
  an in-flight command's response on a short interval, back off exponentially,
  and stop on terminal status or when backgrounded.
- Exit bar for Phase 1. A signed release build ready for Google Play
  internal/closed testing, plus the device-level acceptance and security tests
  of spec §18.
