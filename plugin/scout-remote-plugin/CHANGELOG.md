# Changelog

Notable changes to the `scout-remote-bridge` plugin. Versioning follows SemVer, as
recommended by Agent Plugins 1.0.0 §10.2.

## [0.1.0] — 2026-08-15

### Added

- Initial Agent Plugins 1.0.0 package: the `scout-remote-bridge` skill (with bundled
  protocol quick reference) and the stdio MCP bridge declaration.
- Manifest and MCP configuration validated against the official 1.0.0 schemas
  (specification published 2026-08-06).
- Bridge state directory contract: `SCOUT_REMOTE_STATE_DIR` (backed by `${PLUGIN_DATA}`
  under a conformant client) for delta cursors, receipt mirrors, and diagnostics.
