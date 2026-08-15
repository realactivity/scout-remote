# Changelog

Notable changes to the `lantern-bridge` plugin. Versioning follows SemVer,
as recommended by Agent Plugins 1.0.0 §10.2.

## [0.2.0] (2026-08-15)

### Added

- Stdio MCP server (`ModelContextProtocol` 2.2.0) with deterministic tools:
  `publish_status`, `read_inbox`, `write_acknowledgement`, `write_response`,
  `record_processed_receipt`, `read_activity`, `resolve_artifact`.
- App Folder storage port: filesystem harness (CI) and Graph REST backend
  (`/me/drive/special/approot`, If-Match, Retry-After).
- Strict inbox gate: schema-invalid, unknown-field, expired, and already
  receipted commands are never executed. Prompt-injection text is refused
  and audited.
- `--once` harness mode for the cross-language smoke test.
- State under `LANTERN_STATE_DIR` (receipts mirror, journal, delta cursor).
  MSAL caches stay in a separate user-only directory.

## [0.1.0] (2026-08-15)

### Added

- Initial Agent Plugins 1.0.0 package. The `lantern-bridge` skill (with
  bundled protocol quick reference) and the stdio MCP bridge declaration.
- Manifest and MCP configuration validated against the official 1.0.0 schemas
  (specification published 2026-08-06).
- Bridge state directory contract. `LANTERN_STATE_DIR` (backed by
  `${PLUGIN_DATA}` under a conformant client) holds delta cursors, receipt
  mirrors, and diagnostics.
- Alignment with design spec v2.0: the command state machine in the protocol
  quick reference starts at `acknowledged` (`draft`/`queued` are client-local
  states), and daily usage aggregates live at
  `telemetry/usage/{yyyy-MM-dd}.json`.
- MIT license: standard-layout `LICENSE` at the plugin root plus the matching
  SPDX `license` field in `plugin.json`.
