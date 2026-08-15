# ScoutRemote.App

.NET MAUI client — **Android is the only Phase 0/1 target**; the iOS head is added in Phase 4 against the same shared projects and contract tests (spec §16.1, §19).

This directory intentionally contains no generated project yet: the MAUI project is bootstrapped at the start of Phase 0 on a machine with the MAUI workload installed, so the template matches the SDK actually in use:

```bash
dotnet workload install maui-android
dotnet new maui -n ScoutRemote.App -o src/mobile/ScoutRemote.App -f net8.0
# then: trim TargetFrameworks to net8.0-android for Phase 0/1,
# reference the four src/shared projects, and add the project to ScoutRemote.sln
```

## Rules for this project

- **Android-only APIs live under `Platforms/Android/` only.** Everything reusable belongs in `src/shared/` — that is what makes iOS an implementation phase rather than a rewrite.
- Android platform-service implementations to build here, behind the `ScoutRemote.Platform.Abstractions` interfaces:
  - `ISecureStorage` → Keystore-backed secure storage (holds the MSAL token cache).
  - `IBackgroundScheduler` → background status/response checks within Android scheduling constraints.
  - `INotificationService` → completion / failure / desktop-attention notifications; heartbeat digests only.
  - `IAuthCallbackHandler` → MSAL redirect/browser handling (auth code + PKCE; no client secret in the binary).
  - `IExternalAppLauncher` → open artifacts in Microsoft 365 apps via OneDrive links.
- Screens (spec §9): Home, Activity, Chats, Artifacts, Automations, Usage, Settings. Home must always show explicit freshness (`lastSeenUtc`) — never "online" without evidence.
- Exit bar for Phase 1: signed release build ready for Google Play internal/closed testing, plus the device-level acceptance and security tests of spec §18.
