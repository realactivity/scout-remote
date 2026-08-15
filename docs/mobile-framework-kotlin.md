# Mobile framework decision — Kotlin + Jetpack Compose

**Status:** Accepted, 15 August 2026. Supersedes the ".NET MAUI reference implementation (React Native acceptable)" recommendation of [design spec v1.2](design-spec-v1.2.md) §1/§16. Everything else in the spec stands — this changes the mobile implementation technology, not the architecture. The spec itself sanctions the swap: the §20 invariant makes the framework an implementation detail behind the contracts and domain interfaces.

## Decision

The mobile client is built with **Kotlin and Jetpack Compose**, not .NET MAUI. The desktop bridge stays on .NET (`ScoutRemote.Domain` + `ScoutRemote.Bridge`), where MSAL.NET with Windows broker support is the right tool. The versioned JSON contracts in [`../contracts/`](../contracts/) — not shared code — are the boundary between the two languages, enforced by golden-file contract tests on both sides.

## Why

- **Performance.** Kotlin compiles to ART bytecode with no embedded second runtime. MAUI carries the Mono/.NET runtime alongside ART: slower cold start, ~20–30 MB APK baseline, higher memory floor, and Java-interop marshaling on every Android API call. The cost lands exactly on this app's hottest path — short background wake-ups under WorkManager/Doze for status and response polls (spec §16.2), where spinning up a heavier runtime for a two-second sync is real battery and latency overhead.
- **First-party platform support.** The APIs this design leans on hardest — MSAL Android with broker support, WorkManager, Keystore, notification channels — are documented, tested, and fixed against Kotlin first. MAUI consumes each Android API wave through a binding layer maintained by a team with a well-documented capacity problem.
- **Modernity and tooling.** Compose is a current-generation declarative UI framework evolving quarterly; Kotlin coroutines and structured concurrency map directly onto the asynchronous-mailbox design. MAUI's XAML lineage is the older paradigm, and its tooling reputation is the weakest of the credible options.
- **The code-sharing argument for MAUI got weaker.** MAUI's one real advantage here was compiling shared C# into both the phone app and the bridge. Since the contracts gained language-neutral JSON Schemas with golden examples and conditional validation, schema-driven contract tests are a stronger guarantee than shared types — both sides must *prove* conformance instead of assuming it.

## Consequences

- `src/mobile/` is a Kotlin Gradle project. The `core` module is pure Kotlin/JVM — domain models (kotlinx-serialization against `contracts/schemas/`), application ports (`BridgeTransport`, `CommandService`, `BridgeSyncService`, `IdentityService`), and platform-service interfaces (`SecureStorage`, `BackgroundScheduler`, …). **No Android APIs in `core`** — the spec §16.1 guardrail, now enforced by a module boundary instead of a convention.
- The Android `app` module (Compose UI, MSAL Android, Keystore/WorkManager/notification implementations of the `core` interfaces) is bootstrapped at the start of Phase 0 — see [`../src/mobile/app/README.md`](../src/mobile/app/README.md).
- The mobile-side C# projects (`ScoutRemote.Application`, `ScoutRemote.Transport.Graph`, `ScoutRemote.Platform.Abstractions`) are removed. `ScoutRemote.Domain` remains as the **bridge-side** protocol model; the Kotlin `core` domain package mirrors it against the same schemas, and both run golden-file contract tests over `contracts/examples/`.
- **iOS (Phase 4)** becomes either a Kotlin Multiplatform migration of `core` plus a SwiftUI or Compose Multiplatform head, or a native Swift app implementing the same contracts. That decision is deferred to Phase 4 — the contracts guarantee is what carries over, exactly as the §20 invariant intends. `core` deliberately avoids JVM-only types in its interfaces (URIs are strings, time is kotlinx-datetime/kotlin.time) to keep the KMP door open.
- Two languages are now maintained (Kotlin app, C# bridge). This is judged acceptable: each side uses the language its platform SDKs are built for, and the protocol between them was always language-neutral by design.

## What does not change

The product boundary, the identity model (one Entra public-client registration, independent MSAL sign-ins, no token transfer), the App Folder data plane, the bridge plugin packaging, the command semantics, the security posture, and the roadmap phases — including the latency addendum ([architecture-improvements.md](architecture-improvements.md)) — are all unaffected.
