# Scout's OpenClaw substrate: what Lantern may and may not build on

**Status** Accepted decision record, 15 August 2026. Referenced by
[design spec v2.0](design-spec-v2.0.md) §3, §19, §20, §21. Re-validate per
Scout release; Scout is a preview product with a roughly weekly release cadence
and no user-side rollback.

## Question

Microsoft Scout is built on the open-source OpenClaw runtime. Upstream OpenClaw
natively provides much of what a mobile companion wants: a WebSocket
control-plane gateway (loopback `:18789`), token-based device pairing,
first-class mobile *nodes* (iOS/Android clients connect with `role: node`),
messaging channels (Telegram, WhatsApp, Teams, and others), webhook
ingress/egress, and on-demand `heartbeat_trigger`. Can Lantern build on those
substrate surfaces, or has Microsoft turned them off in Scout?

## Findings (as of 15 August 2026)

Microsoft states Scout is "powered by OpenClaw open-source technology" and
contributes policy conformance upstream. Community analysis confirms Scout uses
the OpenClaw gateway directly, forked and wrapped in an enterprise layer
(per-agent Entra identity, Intune ADMX policies, Purview DLP, Microsoft
Execution Containers sandboxing).

| OpenClaw substrate feature | Status in Scout |
|---|---|
| `SKILL.md` skills | **Kept and documented.** Three tiers: bundled; `~/.copilot/skills/` (per-machine); `~/.copilot/m-skills/` (workspace tier, cloud-synced across the user's devices). |
| MCP servers | **Kept and documented.** Registered via `~/.copilot/mcp-config.json` or the GUI; local stdio (with optional env) and remote HTTP with OAuth; per-server permission scopes and approval prompts. |
| Heartbeat / automations | **Kept, but admin-disableable.** Intune/Group Policy under `HKLM\SOFTWARE\Policies\Scout` includes an explicit "Disable Heartbeat and Automations" control. |
| Gateway WebSocket API for third-party clients | **Present internally, not exposed or documented.** Scout surfaces only a "gateway-auth" UI for MCP OAuth. No supported third-party connect path. |
| Device pairing / mobile nodes | **Not surfaced anywhere in Scout's documentation.** |
| Messaging channels | **Replaced by Teams.** No Telegram/WhatsApp/other community channels in the Microsoft build. |
| Webhook ingress/egress | **Not documented in Scout.** |

Enforcement is real: policies can block specific MCP servers, restrict file and
shell access to the workspace, blocklist browser egress origins, and force
approvals; a continuous "policy conformance system" audits deviations and
produces audit trails.

## Decision

**Lantern builds only on Scout's two exposed extension surfaces — skills and
MCP — and never on the raw OpenClaw substrate.** This is what the bridge plugin
already does; the finding validates the architecture rather than changing it.

The substrate's control-plane surfaces are off-limits even though the gateway
is physically running on the user's machine:

1. **Undocumented in Scout.** Depending on them violates the product boundary
   (spec §2.2, §8.2). Scout ships weekly with no rollback; an undocumented
   socket is the first thing that breaks.
2. **They do not solve the mobile problem.** The gateway binds to loopback.
   Reaching it from a phone means binding off-box or tunneling — the
   anti-pattern that exposed 21,000+ OpenClaw gateways to the public internet
   in January 2026. The architecture's best property is that there is no
   inbound path to the desktop (spec §17).
3. **Policy will eat it.** Enterprise deployments run the conformance auditor
   and Intune policies; an unsanctioned control-plane client is what those
   exist to catch.
4. **Microsoft's control plane is the product.** The runtime is free; identity,
   governance, and distribution are the paid layer. Betting on unexposed
   substrate features is betting against the vendor's business model.

## Consequences

- **Concrete install paths** (previously "re-validate at build time", spec §3):
  skills install to `~/.copilot/skills/` (per-machine) or `~/.copilot/m-skills/`
  (cloud-synced workspace tier — a useful distribution path: the Lantern
  `SKILL.md` follows the user across machines); the MCP bridge registers in
  `~/.copilot/mcp-config.json`. Paths are preview-era and must be re-checked
  per release.
- **New Phase 0, week-one check** (spec §19): confirm the target tenant has not
  policy-disabled Heartbeat/Automations or blocked local stdio MCP servers.
  Without those two, the bridge has no pulse; that is an admin conversation,
  not a code problem.
- **Upgrade trigger sharpened** (spec §20): if Scout ever exposes the gateway
  WebSocket API or OpenClaw-style paired nodes as a *supported* surface, the
  §20 invariant applies — implement it as a new transport adapter behind
  `BridgeTransport`; latency collapses from minutes to seconds and the domain
  model, contracts, and app do not change.

## Sources

- [Introducing Microsoft Scout (Microsoft 365 blog, 2 June 2026)](https://www.microsoft.com/en-us/microsoft-365/blog/2026/06/02/introducing-microsoft-scout-your-always-on-personal-agent/)
- [Use Microsoft Scout (Microsoft Learn)](https://learn.microsoft.com/en-us/microsoft-scout/use-microsoft-scout)
- [Manage Microsoft Scout with Group Policy / Intune (Microsoft Learn)](https://learn.microsoft.com/en-us/microsoft-scout/manage-group-policy)
- [Microsoft launches Scout (TechCrunch, 2 June 2026)](https://techcrunch.com/2026/06/02/microsoft-launches-scout-an-openclaw-inspired-personal-assistant/)
- [Microsoft just made the agent runtime free (The New Stack, 7 June 2026)](https://thenewstack.io/microsoft-scout-openclaw-runtime/)
- [Scout runs on OpenClaw (Till Freitag)](https://till-freitag.com/en/blog/microsoft-scout-openclaw-gateway-en)
- [OpenClaw gateway architecture (upstream docs)](https://github.com/openclaw/openclaw/blob/main/docs/concepts/architecture.md)
