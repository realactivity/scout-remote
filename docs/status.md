# Status

Timestamps over adjectives. Phase 0 is implemented in this repository.
Phase 1 is not started. The project is **paused** for community feedback
([`community.md`](community.md)).

This page is the public split of done vs not-done. Evidence maps:

- automation vs human-gated: [`phase0-report.md`](phase0-report.md)
- production gates: [`enterprise-readiness.md`](enterprise-readiness.md)
- protocol: [`design-spec-v2.0.md`](design-spec-v2.0.md)

## Accomplished

Proven by CI on every pull request. No Microsoft 365 tenant, no emulator, no
Scout process.

| Area | What exists |
|---|---|
| Protocol inbox | Schema validation, unknown-field rejection, inclusive expiry, filename = `commandId`, prompt-injection refuse, exactly-once receipts, terminal-response immutability, protocol-major stop |
| Filesystem store | Create-if-absent, ETag, delta, path escape |
| Graph client (unit-tested) | Conditional reads, item-ID downloads, delta pagination / deletions / 410 reset, origin pin, 429/503 retry with fresh bodies, bounded `Retry-After` |
| Mobile sync | `SyncStateStore`, Android `SqliteSyncStateStore` (SQLite metadata, Keystore payloads), incremental sync |
| Observability | Content-free `LanternMetrics` (no protocol payload in metrics) |
| Desktop package | Agent Plugins 1.0.0 layout: `plugin.json`, `mcp.json`, `skills/lantern-bridge/SKILL.md`, seven MCP tools |
| Desktop identity | MSAL in an OS-protected cache; fails closed if the cache API is unavailable |
| Brand | Tokens in `brand-tokens.json`; Compose theme consumes them; sheet + compact SVGs in `docs/brand/` |
| CI | SHA-pinned actions, locked restores, contracts / .NET / Kotlin / Android / e2e / MCP smoke |

The Agent Plugins package is a production-shaped example of the 1.0 layout.
Scout does **not** install that package natively. Current install is still
copy the skill and register the MCP server.

## Not done

Do not read CI green as "the app works on a phone."

| Area | State |
|---|---|
| Entra public-client app | Not registered for this repo's public story. Android + desktop redirects are human work. |
| Real App Folder round trip | Human-gated. `scripts/smoke-tenant/` exists; `.local/` is never committed. |
| Android Graph / MSAL | Scaffold only. No Graph token in Phase 0 debug. |
| WorkManager | Stub |
| Notifications | Stub |
| Scout policy | Unknown whether heartbeat / automations / local stdio MCP are allowed in a given tenant |
| Interactive phone MSAL | Not demonstrated |
| Desktop token cache on real OS targets | Implemented, not verified on Windows DPAPI / macOS Keychain / Linux Keyring devices |
| Play packaging | No signed release, no SBOM, no internal test track |
| Soak / reliability | Instrumented; 72-hour soak not run |
| iOS | Phase 4 |
| Mode B / C | Out of scope until Mode A is real |
| Trademark / fonts | Open decisions in [`brand-guidelines.md`](brand-guidelines.md) |

## What Phase 0 is not

iOS, Mode B/C, remote approvals, native Scout chat mirroring, a Play Store
listing, or any claim that Microsoft ships or endorses Lantern.

## Next, if the pause lifts

The first product slice after a "build it" decision is still Phase 1: real
Android Graph/MSAL, Entra redirects, and a tenant-proven approot round trip.
That work is **not** started in this branch.
