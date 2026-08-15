# Bridge protocol quick reference

Condensed operational reference for processing Scout Remote commands. The authoritative,
versioned contracts are the JSON Schemas in the source repository (`contracts/`); the MCP
bridge validates every read and write against them — you never parse raw files yourself.

## App Folder layout (`/me/drive/special/approot`)

```
protocol.json                          Handshake: version + capabilities
agents/{agentId}/status.json           Latest status snapshot (mutable)
commands/inbox/{commandId}.json        Mobile → bridge (immutable, ULID-named)
commands/processed/                    Idempotency receipts
responses/{commandId}.json             Bridge → mobile (mutable until terminal)
conversations/{conversationId}/        Append-only thread events
activity/{yyyy-MM-dd}/                 Append-only activity events
telemetry/usage-daily.json             Daily usage aggregate
```

## Command state machine

```
queued -> acknowledged -> running -> completed | failed | expired
                                   | waiting_for_desktop_approval
                                   | canceled   (only when cancellation is CONFIRMED)
```

Write `acknowledged` immediately on pickup — the mobile app fast-polls in-flight
responses and needs early evidence. A response is mutable until it reaches a terminal
status; after that it is immutable and corrections are new events referencing the
original `commandId`.

## Disposition rules

| Situation | Disposition |
|---|---|
| Command past `expiresUtc` | `expired` — never execute, no exceptions |
| Command already has a processed receipt | Skip silently — never re-execute |
| Schema-invalid or ambiguous command | Never execute; the bridge already rejected it |
| Work needs a sensitive/policy-gated action | `waiting_for_desktop_approval` + exact reason |
| `maxWorkIqCalls` bound cannot be met | Partial/blocked response; offer retry with higher budget |
| Cancellation requested but unconfirmed | Report actual state — never claim `canceled` optimistically |

## Command types

| Type | Handling |
|---|---|
| `message` | Bounded instruction under normal Scout policy; respect constraints |
| `request_status` | Publish a fresh status snapshot (deterministic, no Work IQ) |
| `request_summary` | Concise summary of recent bridge activity |
| `request_artifact` | Publish/link an artifact; reference by `driveItemId`, never inline content |
| `cancel_request` | Best effort against `targetCommandId`; confirm before reporting |
