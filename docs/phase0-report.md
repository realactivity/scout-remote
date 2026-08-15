# Phase 0 report

What the Android spike proves automatically, and what still needs a human
with a Microsoft 365 tenant. Timestamps over adjectives.

## Proven by automation

These run in CI on every pull request. No tenant, no emulator, no Scout
process.

| Claim | Evidence |
|---|---|
| Contract golden files still round-trip on both sides | `ContractGoldenFileTests` (.NET), `ContractGoldenFileTest` (Kotlin), `python3 contracts/validate_examples.py` |
| Filesystem App Folder store: create-if-absent, ETag, delta | `FilesystemStoreTests` |
| One command, exactly once, ack then complete | `InboxProcessorTests.ProcessOnceAcknowledgesThenCompletesExactlyOnce`, `CrossLanguageSmokeTest` |
| Expired command: `expired` disposition, never executed | `InboxProcessorTests.ExpiredCommandGetsExpiredDispositionAndNeverExecutes` |
| Replay / duplicate inbox file skipped | `InboxProcessorTests.ReplayAfterReceiptIsSkipped` |
| Schema-invalid and unknown-field commands rejected with an audit event | `InboxProcessorTests.UnknownFieldAndSchemaInvalidAreRejectedWithAuditEvent` |
| Prompt-injection text refused and audited | `InboxProcessorTests.PromptInjectionIsRefusedAndAudited` |
| No secrets in the harness approot after a run | `InboxProcessorTests.NoSecretsInApprootAfterProcess`, e2e sweep |
| MCP stdio: initialize, list tools, `publish_status`, `read_inbox`, no credentials | `McpStdioSmokeTests` |
| Kotlin `CommandService` / `BridgeSyncService` / ULID / protocol gate | `CommandServiceTest`, `BridgeSyncServiceTest`, `UlidTest` |
| Android app compiles; theme hexes come from `docs/brand-tokens.json` | `:app:assembleDebug`, `BrandTokensTest` |

## Human-gated (not run in CI)

| Claim | How to prove it |
|---|---|
| `Files.ReadWrite.AppFolder` in the real tenant | `scripts/smoke-tenant/README.md` |
| Desktop and mobile-sim resolve the same approot | same kit, §21.1 steps |
| Scout heartbeat / automations and local stdio MCP are allowed | printed `HUMAN` rows in the tenant kit; see `docs/scout-openclaw-substrate.md` |
| Interactive MSAL on a phone (PKCE, Keystore cache) | install the debug APK after Entra Android redirect is registered |
| Emulator / device Home screen | not assumed in CI |

## Commands

```bash
# Existing suites
dotnet build Lantern.sln -c Release
dotnet test --solution Lantern.sln -c Release --no-build
( cd src/mobile && ./gradlew :core:build )
python3 contracts/validate_examples.py

# Cross-language e2e (CI job smoke-e2e)
scripts/smoke-e2e/run.sh

# Android app + theme unit tests (CI job android)
( cd src/mobile && ./gradlew :app:assembleDebug :app:testDebugUnitTest )

# Real tenant (human only)
python3 scripts/smoke-tenant/smoke.py
```

## Defaults recorded

| Ambiguity | Default chosen | Why |
|---|---|---|
| Receipt file has no public schema | Bridge-private JSON under `commands/processed/` plus a state-dir mirror | Avoid a silent contract change |
| Phase 0 execution without Scout | Deterministic echo / status text in `--once` | Makes the §19 exit bar automatable |
| Unsigned Android send | `requestedBy.entraObjectId = unsigned-local-dev` | Audit metadata, never authorization (§11.2) |
| Font files | System sans / mono mapped to Space Grotesk, Roboto Flex, IBM Plex Mono roles | Brand guidelines still list font licensing as an open decision |
| Graph SDK | Thin Graph REST client (HttpClient / Java HttpClient) over MSAL | Retry-After and If-Match stay explicit |

## What Phase 0 is not

iOS, Mode B/C, remote approvals, native Scout chat mirroring, schema
changes, Play Store signing, or any edit to `docs/design-spec-v1.2.*`.
