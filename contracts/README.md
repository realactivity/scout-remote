# contracts/

Shared bridge-protocol contracts. Versioned JSON exchanged through the OneDrive
App Folder between the mobile client and the desktop bridge (spec §6 to §7).
These files are the **stable contract** between all clients. The mobile app, the
desktop MCP bridge/helper, and any future adapter implement *these* files, not
each other's internals.

- `schemas/` holds JSON Schema (draft 2020-12) definitions, one per protocol
  file type. They are the **writer contract**: every producer must emit
  payloads that validate against them, and they declare
  `additionalProperties: false` so a writer can never smuggle undeclared
  fields into the protocol.
- `examples/` holds the canonical payloads from the design spec (§6, §7, §13).
  They are the golden files in the contract tests on both sides, and CI
  validates each one against its schema.
- `validate_examples.py` is the validation script CI runs; it can be run
  locally with any Python 3 plus `jsonschema`.

## Rules

1. **Versioned evolution: tolerant reader, strict writer (spec §15.2).**
   Every payload carries `schemaVersion`; the protocol handshake carries
   `version` (`major.minor`). Writers validate strictly against the current
   schema. Readers of **bridge-authored** files (status, responses, events,
   usage, handshake) ignore unknown fields, so the bridge can add optional
   fields in a minor revision without breaking deployed clients. The one
   deliberate exception is the **command inbox**: commands cause execution, so
   the bridge rejects (never executes) any command that fails strict schema
   validation, including one carrying unknown fields. Mobile-authored evolution
   is therefore gated by the handshake: the mobile client only sends command
   types and fields covered by a capability the bridge advertises. Breaking
   changes (remove/re-type/semantics) bump the major version, and consumers
   must stop processing incompatible major versions.
2. **No secrets.** The schema deliberately has no fields for API keys, refresh
   tokens, passwords, device codes, connection strings, or auth headers, and
   none may be added (spec §2.2, §11).
3. **Untrusted input.** All free-text fields are untrusted at the consumer.
   Validate structure first. Treat text as data, not instructions
   (prompt-injection posture, spec §11). Client-asserted fields such as
   `requestedBy.entraObjectId` are audit metadata, never authorization inputs
   (spec §11.2).
4. **Immutability.** Commands and conversation/activity events are immutable
   once written. A response file is mutable while the command is in flight.
   The bridge updates it through `acknowledged`/`running` (write the
   acknowledgement promptly, spec §5.1). It becomes immutable once it reaches
   a terminal status (spec §15.1). Corrections after that are new events
   referencing the original `commandId`. The current day's usage aggregate is
   mutable; a closed day is immutable. Consumers use ETags for all mutable
   files.
5. **Both sides prove conformance.** The bridge-side .NET domain model
   (`src/shared/Lantern.Domain`, tested by
   `src/shared/Lantern.Domain.Tests`) and the mobile Kotlin domain model
   (`src/mobile/core`, tested by `ContractGoldenFileTest`) each run
   golden-file round-trip tests over `examples/`. CI additionally validates
   every example against its schema with an independent draft 2020-12
   validator (`validate_examples.py`). Tolerant-reader behavior is itself
   under test on both sides.

## Protocol layout in the App Folder

```
approot/
├── protocol.json                    schemas/protocol.schema.json
├── agents/{agentId}/status.json     schemas/agent-status.schema.json
├── commands/inbox/{commandId}.json  schemas/command.schema.json
├── commands/processed/              idempotency receipts
├── responses/{commandId}.json       schemas/response.schema.json
├── conversations/{conversationId}/
│   └── {eventId}.json               schemas/conversation-event.schema.json
├── activity/{yyyy-MM-dd}/
│   └── {eventId}.json               schemas/activity-event.schema.json
└── telemetry/usage/
    └── {yyyy-MM-dd}.json            schemas/usage-daily.schema.json
```
