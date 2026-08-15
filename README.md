# Lantern

**Signals, not vibes.** A secure mobile companion for Microsoft Scout. Check what
Scout is doing, review bridge-owned chats and activity, send bounded instructions,
view artifacts, and understand usage, without depending on undocumented Scout
internals.

Lantern ships as **"Lantern for Microsoft Scout"** — the nominative pattern (like
Apollo for Reddit, Ivory for Mastodon): a distinct product name with "for Microsoft
Scout" as a descriptor, verified against Microsoft's trademark guidelines. Brand and
UX specs are in [`docs/brand-guidelines.md`](docs/brand-guidelines.md).

> **Status** Proposed reference architecture (spec v1.2, 14 August 2026).
> Pre-implementation scaffolding.
>
> **Authoritative design** [`docs/design-spec-v1.2.md`](docs/design-spec-v1.2.md)
> ([original PDF](docs/Scout-Remote-Design-Spec-v1.2.pdf)).

## Product boundary

Lantern is a **companion to the documented Windows/macOS Scout desktop
application**, not a replacement for it, and not a native Scout runtime on
mobile. The design deliberately does **not** assume

- a public API for native Scout chat, session control, enumeration, or posting
- a supported way to remotely approve Scout's sensitive actions (approvals stay
  on the desktop, under Scout's own policy)
- that Scout natively installs Agent Plugins 1.0 packages (the plugin is the
  portable source of truth; its skill and MCP server are installed through
  Scout-supported mechanisms)
- any parsing or mutation of undocumented Scout databases, caches, or session
  files

The stable contract is the **app-owned bridge protocol**. Versioned JSON is
exchanged through the OneDrive App Folder, produced and consumed by the desktop
bridge (Agent Plugin package) on one side and the mobile client on the other.
Mobile can *request*. Desktop Scout *decides*.

## Android-first sequencing

Launch sequencing is explicit in v1.2.

- **Phase 0 (spike) and Phase 1 (MVP) target Android only.** Entra public-client
  registration is configured for Android and desktop redirects. The first
  release covers Keystore-backed secure storage, Android background scheduling,
  notifications, MSAL redirect handling, and Google Play internal/closed-test
  packaging.
- **iOS is Phase 4**, on the same shared domain, transport, identity, and
  platform-service abstractions. iOS-specific Entra configuration, Keychain
  storage, background behavior, and App Store work are deferred until that
  phase. They do not block the Android MVP.
- **Shared domain, application-service, transport, and protocol code stays free
  of Android-only APIs.** That code lives in the pure-JVM Kotlin `core` module.
  Platform specifics implement its interfaces (`SecureStorage`,
  `BackgroundScheduler`, `NotificationService`, and others) in the Android
  `app` module, so iOS is an implementation and testing phase, not an
  architectural rewrite.

## Architecture summary

Asynchronous by design. OneDrive is a durable mailbox and state store, so the
phone and the PC never need a direct network connection. The app stays useful
when Scout is offline for hours.

```
  Single-user Microsoft 365 trust boundary
  -----------------------------------------

  Mobile client                 Microsoft 365 data plane      Scout desktop
  Android first, iOS planned    Microsoft Graph               Agent Plugin package
  MSAL public client            OneDrive App Folder           SKILL.md orchestration
  shared app contracts          versioned command/response    local MCP bridge/helper
  secure local token cache      protocol + driveItem          MSAL public client
                                artifacts                     (same Entra app ID,
                                                               separate token cache)
                                                              heartbeat / automation
                                                              approvals stay local
```

Key decisions are in spec §1 and §3 to §7.

| Area | Decision |
|---|---|
| Identity | **One** Entra public-client app registration shared by mobile and desktop bridge. Each client signs in independently with MSAL and keeps its own OS-protected token cache. **Tokens are never transferred between devices.** |
| Data plane | Microsoft Graph and the OneDrive **App Folder** (`Files.ReadWrite.AppFolder`). User-owned, least-privilege, cross-device. Both clients resolve the same `/me/drive/special/approot`. |
| Desktop bridge | A **conformant Agent Plugins 1.0.0** bundle, validated against the official schemas. See [`plugin/lantern-plugin/README.md`](plugin/lantern-plugin/README.md). A Scout `SKILL.md` handles orchestration. A **local MCP bridge/helper** owns Graph authentication and protocol I/O. |
| Chat model | **Bridge-owned conversations.** Append-only event streams, reproducible from protocol data. Native Scout chat mirroring is an optional, feature-flagged future adapter. |
| Command semantics | Every command has an ID, timestamp, **expiry**, status, and auditable result. At-least-once observation, exactly-once effect. Idempotency uses processed receipts. Expired, replayed, or malformed commands never execute. |
| Safety | Sensitive work returns `waiting_for_desktop_approval`. There is no remote bypass of Scout approvals. Protocol text is treated as untrusted input (prompt-injection posture). No secrets ever land in OneDrive protocol JSON. |
| Mobile framework | **Kotlin and Jetpack Compose.** This supersedes the spec's MAUI recommendation. See [`docs/mobile-framework-kotlin.md`](docs/mobile-framework-kotlin.md). The Graph transport can be swapped for a native Scout API adapter if Microsoft ships one. The desktop bridge stays on .NET. The `contracts/` schemas are the language-neutral boundary. |
| Deployment | Mode A (personal, no backend) for MVP. Optional notification service (Mode B) and enterprise brokering (Mode C) later. |

## Repository layout

```
lantern/
├── docs/                             Reference architecture (spec v1.2, markdown and PDF)
├── contracts/                        Shared bridge-protocol contracts (JSON Schema and examples)
│   ├── schemas/                      protocol, status, command, response, events, usage
│   └── examples/                     Canonical payloads from the spec
├── src/
│   ├── shared/
│   │   └── Lantern.Domain/       Bridge-side .NET protocol model
│   └── mobile/                       Kotlin Gradle project
│       ├── core/                     Pure Kotlin/JVM. No Android APIs.
│       │                             Domain models, ports, platform interfaces,
│       │                             golden-file contract tests
│       └── app/                      Android client (Compose and MSAL).
│                                     Bootstrapped at Phase 0. iOS in Phase 4.
├── plugin/
│   └── lantern-plugin/          Desktop bridge. Agent Plugins 1.0.0 package.
│       ├── plugin.json               Identity and metadata
│       ├── mcp.json                  Typed stdio MCP server
│       ├── CHANGELOG.md              SemVer history
│       ├── skills/lantern-bridge/
│       └── bridge/Lantern.Bridge/
└── Lantern.sln
```

## Getting started (Phase 0 spike)

Build-time validation comes first. Scout is a Frontier product and Agent Plugins
1.0 is new, so re-validate platform assumptions before coding (spec §3 and
Appendix C).

1. Re-check Scout docs for native Agent Plugins installation and MCP
   registration paths.
2. Confirm `Files.ReadWrite.AppFolder` support in the target Microsoft 365
   tenant.
3. Register **one** Entra public-client app with Android and desktop/native
   redirects (defer iOS platform config to Phase 4). No client secret ships in
   any client.
4. Install the plugin's `SKILL.md` into a supported Scout skills directory and
   register the local MCP bridge through Scout's supported MCP configuration.
   Run the desktop bridge sign-in once.
5. Prove the round trip. Android and desktop independently resolve the same
   `/me/drive/special/approot`. A hand-authored command is processed exactly
   once, and a response lands back on the phone.

The full first-time setup checklist and troubleshooting order are in the spec's
operational runbook (§21).

Accepted refinements sit in
[`docs/architecture-improvements.md`](docs/architecture-improvements.md). They
cover adaptive foreground polling for in-flight commands (Phase 1), content-free
Mode B push notifications when interactive use grows, and validating the
`Files.ReadWrite.AppFolder` tenant assumption in week one of Phase 0, before any
feature code. The mobile client uses Kotlin and Jetpack Compose rather than the
spec's MAUI recommendation. That decision is recorded in
[`docs/mobile-framework-kotlin.md`](docs/mobile-framework-kotlin.md).

## Roadmap

| Phase | Scope |
|---|---|
| 0 | Android spike. MSAL on both ends, App Folder round trip, hand-authored protocol files. |
| 1 | **Android MVP.** Home, Activity, Chats, Artifacts, Settings. Packaged skill and MCP bridge. Play internal test. |
| 2 | Cost and polish. Usage screen, Work IQ budgets, diagnostics, retention controls. |
| 3 | Optional Azure notification service and Graph subscriptions. |
| 4 | **iOS.** Platform config, Keychain, background work, notifications, App Store flow, same contract tests. |
| 5 | Adapters. Official Scout API, native-chat mirror, optional Dynamics 365 direct. |
| 6 | Enterprise. Admin policy, audit export, managed deployment. |

**Architecture invariant.** Even if Microsoft later ships first-party Scout
mobile APIs, the domain-level interfaces (status, activity, remote command,
response, artifact, usage) stay put. Swap the transport adapter. Do not rewrite
the app.
