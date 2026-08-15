# Changelog

Notable changes to the `lantern-bridge` plugin. Versioning follows SemVer,
as recommended by Agent Plugins 1.0.0 §10.2.

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
