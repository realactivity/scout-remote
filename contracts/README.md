# contracts/

Shared bridge-protocol contracts: the versioned JSON exchanged through the OneDrive App Folder between the mobile client and the desktop bridge (spec §6–§7). These are the **stable contract** between all clients — the mobile app, the desktop MCP bridge/helper, and any future adapter implement *these* files, not each other's internals.

- `schemas/` — JSON Schema (draft 2020-12) definitions, one per protocol file type.
- `examples/` — canonical payloads taken verbatim from the design spec; used as golden files in contract tests.

## Rules

1. **Versioned evolution.** Every payload carries `schemaVersion` (protocol handshake carries `version`). Additive changes bump minor behavior only; breaking changes bump the major version, and consumers must stop processing incompatible major versions (spec §15).
2. **No secrets.** The schema deliberately has no fields for API keys, refresh tokens, passwords, device codes, connection strings, or auth headers — and none may be added (spec §2.2, §11).
3. **Untrusted input.** All free-text fields are untrusted at the consumer: validate schema first, treat text as data not instructions (prompt-injection posture, spec §11).
4. **Immutability.** Commands and conversation/activity events are immutable once written. A response file is mutable while the command is in flight — the bridge updates it through `acknowledged`/`running` (write the acknowledgement promptly, spec §5.1) — and becomes immutable once it reaches a terminal status (spec §15.1). Corrections after that are new events referencing the original `commandId`. Consumers therefore use ETags for in-flight responses just as for other mutable files.
5. Both the .NET domain model (`src/shared/ScoutRemote.Domain`) and the bridge helper serialize to/from these schemas; contract tests on both sides validate against `schemas/` using `examples/` as fixtures.

## Protocol layout in the App Folder

```
approot/
├── protocol.json                    → schemas/protocol.schema.json
├── agents/{agentId}/status.json     → schemas/agent-status.schema.json
├── commands/inbox/{commandId}.json  → schemas/command.schema.json
├── commands/processed/              (idempotency receipts)
├── responses/{commandId}.json       → schemas/response.schema.json
├── conversations/{conversationId}/
│   └── {eventId}.json               → schemas/conversation-event.schema.json (append-only)
├── activity/{yyyy-MM-dd}/
│   └── {eventId}.json               → schemas/activity-event.schema.json (append-only)
└── telemetry/usage-daily.json       → schemas/usage-daily.schema.json
```
