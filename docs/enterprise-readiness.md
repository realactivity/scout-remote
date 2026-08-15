# Enterprise readiness

Lantern is not yet a production enterprise release. Phase 0 is an
enterprise-quality protocol and harness foundation with explicit release
gates. This page prevents local and CI proof from being confused with tenant,
device, deployment, or governance proof.

## Automated controls

| Control | Evidence |
|---|---|
| Strict command inbox | JSON Schema validation, unknown-field rejection, inclusive expiry, filename and `commandId` binding |
| Replay safety | Create-if-absent receipts, existing-response fail-closed behavior, terminal-response receipt repair |
| Protocol compatibility | Bridge and mobile stop on an incompatible protocol major |
| Concurrency | ETag updates, immutable terminal responses, locked and atomic local state writes |
| Graph resilience | 429/503 retry with bounded `Retry-After`, fresh request bodies per attempt, request timeouts |
| Secret hygiene | No credentials in protocol schemas; MCP and e2e secret scans; MSAL cache outside protocol/state directories |
| Cross-language compatibility | .NET and Kotlin golden files plus filesystem end-to-end smoke |
| Mobile architecture | Pure JVM core, lifecycle-owned ViewModel, durable local command retry, foreground in-flight watch |
| Efficient mobile sync | SQLite metadata/protected payload cache plus delta/ETag/item-ID request-budget tests |
| Runtime observability | Typed content-free operation, duration, retry, cache-count, and sync-age metrics with Logcat privacy tests |
| Supply chain | SHA-pinned CI actions, exact CI dependencies, dependency update automation |
| Governance | `SECURITY.md`, `CONTRIBUTING.md`, DCO sign-off, least-privilege workflow permissions |

## Release gates

These must pass before calling the Android client or desktop bridge
production-ready:

| Gate | Current state | Required evidence |
|---|---|---|
| Entra registration | Human-gated | Android and desktop redirects; correct tenant/account binding |
| Real Graph App Folder | Human-gated | Both clients independently resolve the same approot and complete a command round trip |
| Android Graph/MSAL wiring | Not implemented | Graph store selected outside harness mode; silent-first MSAL; protected cache verified on device |
| Desktop protected token cache | Implemented, unverified on target devices | DPAPI, macOS Keychain, and Linux Keyring behavior verified on supported desktop targets |
| Scout policy and pulse | Human-gated | Local stdio MCP and heartbeat/automations allowed by tenant policy |
| Android background behavior | Stub | WorkManager implementation tested under Doze, standby, restart, and network loss |
| Notifications | Stub | Completion, failure, and desktop-attention notifications tested without protocol content leakage |
| Packaging | Not started | Signed release, SBOM, provenance, vulnerability scan, Play internal/closed test |
| Reliability | Instrumented, soak not run | 72-hour soak, throttling tests, crash recovery, offline queue recovery |
| Security acceptance | Partial | Device loss, account revocation, replay, injection, token inspection, dependency scan |

## Deployment boundary

Mode A is the only current target: one Microsoft 365 user, Android client,
desktop bridge, and OneDrive App Folder. Mode B and Mode C require their own
threat-model review. Device-bound command signing is required before Mode C and
recommended before any shared or brokered deployment.

The authoritative feature and release sequence remains
`docs/design-spec-v2.0.md`. `docs/phase0-report.md` is the detailed evidence
map for the current spike.
