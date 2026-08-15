# Scout Remote

**A secure mobile companion for Microsoft Scout** — check what Scout is doing, review bridge-owned chats and activity, send bounded instructions, view artifacts, and understand usage, without depending on undocumented Scout internals.

> **Status:** Proposed reference architecture (spec v1.2, 14 August 2026). Pre-implementation scaffolding.
> Authoritative design: [`docs/design-spec-v1.2.md`](docs/design-spec-v1.2.md) ([original PDF](docs/Scout-Remote-Design-Spec-v1.2.pdf)).

## Product boundary

Scout Remote is a **companion to the documented Windows/macOS Scout desktop application, not a replacement for it**, and not a native Scout runtime on mobile. The design deliberately does **not** assume:

- a public API for native Scout chat/session control, enumeration, or posting;
- a supported way to remotely approve Scout's sensitive actions — approvals stay on the desktop, under Scout's own policy;
- that Scout natively installs Agent Plugins 1.0 packages (the plugin is the portable source-of-truth; its skill and MCP server are installed through Scout-supported mechanisms);
- any parsing or mutation of undocumented Scout databases, caches, or session files.

The stable contract is the **app-owned bridge protocol**: versioned JSON exchanged through the OneDrive App Folder, produced and consumed by the desktop bridge (Agent Plugin package) on one side and the mobile client on the other. Mobile can *request*; desktop Scout *decides*.

## Android-first sequencing

Launch sequencing is explicit in v1.2:

- **Phase 0 (spike) and Phase 1 (MVP) target Android only** — Entra public-client registration configured for Android + desktop redirects, Keystore-backed secure storage, Android background scheduling, notifications, MSAL redirect handling, and Google Play internal/closed-test packaging.
- **iOS is Phase 4**, on the same shared domain, transport, identity, and platform-service abstractions. iOS-specific Entra configuration, Keychain storage, background behavior, and App Store work are deferred until that phase — they do not block the Android MVP.
- The guardrail that keeps this honest: **shared domain, application-service, transport, and protocol code stays free of Android-only APIs.** It lives in the pure-JVM Kotlin `core` module; platform specifics implement its interfaces (`SecureStorage`, `BackgroundScheduler`, `NotificationService`, …) in the Android `app` module, so iOS is an implementation-and-testing phase, not an architectural rewrite.

## Architecture summary

Asynchronous by design: OneDrive is a durable mailbox/state store, so the phone and the PC never need a direct network connection and the app stays useful when Scout is offline for hours.

```
┌────────────────────────── Single-user Microsoft 365 trust boundary ──────────────────────────┐
│                                                                                              │
│  Mobile client                    Microsoft 365 data plane           Scout desktop           │
│  (Android first, iOS planned)     Microsoft Graph                    Agent Plugin package    │
│  MSAL public client          ◄──► OneDrive App Folder           ◄──► SKILL.md orchestration  │
│  shared app contracts             versioned command/response         local MCP bridge/helper │
│  secure local token cache         protocol + driveItem artifacts     MSAL public client      │
│                                                                      (same Entra app ID,     │
│                                                                       separate token cache)  │
│                                                                      heartbeat / automation  │
│                                                                      approvals stay local    │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

Key decisions (see spec §1 and §3–§7 for full rationale):

| Area | Decision |
|---|---|
| Identity | **One** Entra public-client app registration shared by mobile and desktop bridge; each client signs in independently with MSAL and keeps its own OS-protected token cache. **Tokens are never transferred between devices.** |
| Data plane | Microsoft Graph + OneDrive **App Folder** (`Files.ReadWrite.AppFolder`) — user-owned, least-privilege, cross-device. Both clients resolve the same `/me/drive/special/approot`. |
| Desktop bridge | Packaged as an **Agent Plugins 1.0** bundle: a Scout `SKILL.md` for orchestration plus a **local MCP bridge/helper** that owns Graph authentication and protocol I/O. |
| Chat model | **Bridge-owned conversations** — append-only event streams reproducible from protocol data. Native Scout chat mirroring is an optional, feature-flagged future adapter. |
| Command semantics | Every command has an ID, timestamp, **expiry**, status, and auditable result. At-least-once observation, exactly-once effect: idempotency via processed receipts; expired/replayed/malformed commands never execute. |
| Safety | Sensitive work returns `waiting_for_desktop_approval` — there is no remote bypass of Scout approvals. Protocol text is treated as untrusted input (prompt-injection posture). No secrets ever land in OneDrive protocol JSON. |
| Mobile framework | **Kotlin + Jetpack Compose** (supersedes the spec's MAUI recommendation — see [`docs/mobile-framework-kotlin.md`](docs/mobile-framework-kotlin.md)); layered so the Graph transport can be swapped for a native Scout API adapter if Microsoft ships one. The desktop bridge stays on .NET; the `contracts/` schemas are the language-neutral boundary. |
| Deployment | Mode A (personal, no backend) for MVP; optional notification service (Mode B) and enterprise brokering (Mode C) later. |

## Repository layout

```
scout-remote/
├── docs/                             Reference architecture (spec v1.2: markdown + original PDF)
├── contracts/                        Shared bridge-protocol contracts (JSON Schema + examples)
│   ├── schemas/                      protocol / status / command / response / conversation-event / activity-event / usage schemas
│   └── examples/                     Canonical payloads from the spec
├── src/
│   ├── shared/
│   │   └── ScoutRemote.Domain/       Bridge-side .NET protocol model (serializes contracts/ schemas)
│   └── mobile/                       Kotlin Gradle project (framework decision: docs/mobile-framework-kotlin.md)
│       ├── core/                     Pure Kotlin/JVM — NO Android APIs: domain models, ports
│       │                             (BridgeTransport, CommandService, …), platform interfaces,
│       │                             golden-file contract tests against contracts/examples
│       └── app/                      Android client (Compose + MSAL) — bootstrapped at Phase 0;
│                                     iOS follows in Phase 4 against the same core + contracts
├── plugin/
│   └── scout-remote-plugin/          Desktop bridge as an Agent Plugins 1.0 package
│       ├── plugin.json               Package identity (Agent Plugins 1.0 manifest)
│       ├── mcp.json                  Local MCP bridge/helper server declaration
│       ├── skills/scout-remote-bridge/SKILL.md   Agent-facing orchestration instructions
│       └── bridge/ScoutRemote.Bridge/            MCP helper: desktop MSAL + Graph + protocol I/O
└── ScoutRemote.sln
```

## Getting started (Phase 0 spike)

Build-time validation comes first — Scout is a Frontier product and Agent Plugins 1.0 is new, so re-validate platform assumptions before coding (spec §3 and Appendix C):

1. Re-check Scout docs for native Agent Plugins installation and MCP registration paths.
2. Confirm `Files.ReadWrite.AppFolder` support/status in the target Microsoft 365 tenant.
3. Register **one** Entra public-client app with Android + desktop/native redirects (defer iOS platform config to Phase 4). No client secret ships in any client.
4. Install the plugin's `SKILL.md` into a supported Scout skills directory and register the local MCP bridge through Scout's supported MCP configuration; run the desktop bridge sign-in once.
5. Prove the round trip: Android and desktop independently resolve the same `/me/drive/special/approot`, a hand-authored command is processed exactly once, and a response lands back on the phone.

The full first-time setup checklist and troubleshooting order are in the spec's operational runbook (§21).

Accepted refinements on top of the spec are recorded in [`docs/architecture-improvements.md`](docs/architecture-improvements.md): adaptive foreground polling for in-flight commands (Phase 1), content-free Mode B push notifications when interactive use grows, and validating the `Files.ReadWrite.AppFolder` tenant assumption in week one of Phase 0 — before any feature code. The mobile client is built with Kotlin + Jetpack Compose rather than the spec's MAUI recommendation; that decision and its consequences are recorded in [`docs/mobile-framework-kotlin.md`](docs/mobile-framework-kotlin.md).

## Roadmap

| Phase | Scope |
|---|---|
| 0 | Android spike — MSAL both ends, App Folder round trip, hand-authored protocol files |
| 1 | **Android MVP** — Home/Activity/Chats/Artifacts/Settings, packaged skill + MCP bridge, Play internal test |
| 2 | Cost + polish — Usage screen, Work IQ budgets, diagnostics, retention controls |
| 3 | Optional Azure notification service + Graph subscriptions |
| 4 | **iOS** — platform config, Keychain, background/notifications, App Store flow, same contract tests |
| 5 | Adapters — official Scout API / native-chat mirror / optional Dynamics 365 direct |
| 6 | Enterprise — admin policy, audit export, managed deployment |

**Architecture invariant:** even if Microsoft later ships first-party Scout mobile APIs, the domain-level interfaces (status, activity, remote command, response, artifact, usage) stay put — swap the transport adapter, don't rewrite the app.
