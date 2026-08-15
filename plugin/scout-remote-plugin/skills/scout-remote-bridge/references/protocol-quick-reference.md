# Bridge protocol quick reference

Condensed operational reference for processing Scout Remote commands. The
authoritative, versioned contracts are the JSON Schemas in the source
repository (`contracts/`). The MCP bridge validates every read and write
against them. You never parse raw files yourself.

## App Folder layout (`/me/drive/special/approot`)

```
protocol.json                          Handshake (version and capabilities)
agents/{agentId}/status.json           Latest status snapshot (mutable)
commands/inbox/{commandId}.json        Mobile to bridge (immutable, ULID-named)
commands/processed/                    Idempotency receipts
responses/{commandId}.json             Bridge to mobile (mutable until terminal)
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

Write `acknowledged` immediately on pickup. The mobile app fast-polls
in-flight responses and needs early evidence. A response is mutable until it
reaches a terminal status. After that it is immutable, and corrections are new
events referencing the original `commandId`.

## Disposition rules

| Situation | Disposition |
|---|---|
| Command past `expiresUtc` | `expired`. Never execute. No exceptions. |
| Command already has a processed receipt | Skip silently. Never re-execute. |
| Schema-invalid or ambiguous command | Never execute. The bridge already rejected it. |
| Work needs a sensitive/policy-gated action | `waiting_for_desktop_approval` plus exact reason |
| `maxWorkIqCalls` bound cannot be met | Partial/blocked response. Offer retry with higher budget. |
| Cancellation requested but unconfirmed | Report actual state. Never claim `canceled` optimistically. |

## Command types

| Type | Handling |
|---|---|
| `message` | Bounded instruction under normal Scout policy. Respect constraints. |
| `request_status` | Publish a fresh status snapshot (deterministic, no Work IQ). |
| `request_summary` | Concise summary of recent bridge activity. |
| `request_artifact` | Publish or link an artifact. Reference by `driveItemId`. Never inline content. |
| `cancel_request` | Best effort against `targetCommandId`. Confirm before reporting. |
