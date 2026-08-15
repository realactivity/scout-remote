# Scout's OpenClaw substrate: decision record and adoption roadmap

| | |
|---|---|
| Status | Accepted, 15 August 2026 |
| Decision | Lantern builds only on Scout's documented extension surfaces (skills + MCP), never on the raw OpenClaw substrate |
| Referenced by | [design spec v2.0](design-spec-v2.0.md) §3, §19, §20, §21 |
| Review cadence | Re-validate at every phase boundary (spec §19) and on every Scout release-note review; Scout is preview, ships roughly weekly, no user-side rollback |

This record has two jobs. The first half fixes the decision and its evidence in
time so nobody re-litigates it from memory. The second half is the forward
plan: Scout's substrate has far more capability than Microsoft currently
exposes, Microsoft is documenting more of it as the product matures, and this
architecture is deliberately shaped to absorb each unlock as a bounded adapter
change rather than a redesign. When Microsoft documents more, we improve the
architecture by executing the pre-agreed stage below — not by debating the
boundary again.

## 1. Question

Microsoft Scout is built on the open-source OpenClaw runtime. Upstream OpenClaw
natively provides much of what a mobile companion wants:

- a WebSocket control-plane **gateway** (loopback `:18789`) with a typed
  request/response/event protocol,
- token-based **device pairing** with per-device scopes,
- first-class **mobile nodes** — iOS/Android clients connect with
  `role: node`, declare capabilities, and receive commands,
- **messaging channels** (Telegram, WhatsApp, Teams, Discord, Signal, and
  others) multiplexed through the same gateway,
- **webhook ingress/egress** for event-driven integration,
- on-demand **`heartbeat_trigger`** and a cron/automations scheduler.

Can Lantern build on those substrate surfaces, or has Microsoft turned them
off in Scout?

## 2. Findings (as of 15 August 2026)

Microsoft states Scout is "powered by OpenClaw open-source technology" and
contributes policy conformance upstream. Community analysis confirms Scout
uses the OpenClaw gateway directly — a fork wrapped in an enterprise layer:
per-agent Entra identity, Intune ADMX policies, Purview DLP, and Microsoft
Execution Containers sandboxing.

| OpenClaw substrate feature | Status in Scout |
|---|---|
| `SKILL.md` skills | **Kept and documented.** Three tiers: bundled; `~/.copilot/skills/` (per-machine); `~/.copilot/m-skills/` (workspace tier, cloud-synced across the user's devices). |
| MCP servers | **Kept and documented.** Registered via `~/.copilot/mcp-config.json` or the GUI; local stdio (with optional env) and remote HTTP with OAuth; per-server permission scopes and approval prompts. |
| Heartbeat / automations | **Kept, but admin-disableable.** Intune/Group Policy under `HKLM\SOFTWARE\Policies\Scout` includes an explicit "Disable Heartbeat and Automations" control. |
| Gateway WebSocket API for third-party clients | **Present internally, not exposed or documented.** Scout surfaces only a "gateway-auth" UI for MCP OAuth. No supported third-party connect path. |
| Device pairing / mobile nodes | **Not surfaced anywhere in Scout's documentation.** |
| Messaging channels | **Replaced by Teams.** No Telegram/WhatsApp/other community channels in the Microsoft build. |
| Webhook ingress/egress | **Not documented in Scout.** |

Enforcement is real: policies can block specific MCP servers, restrict file
and shell access to the workspace, blocklist browser egress origins, and force
approvals; a continuous "policy conformance system" audits deviations and
produces audit trails.

## 3. Decision

**Lantern builds only on Scout's two exposed extension surfaces — skills and
MCP — and never on the raw OpenClaw substrate.** This is what the bridge
plugin already does; the finding validates the architecture rather than
changing it.

The substrate's control-plane surfaces are off-limits even though the gateway
is physically running on the user's machine:

1. **Undocumented in Scout.** Depending on them violates the product boundary
   (spec §2.2, §8.2). Scout ships weekly with no rollback; an undocumented
   socket is the first thing that breaks, and it breaks on Microsoft's
   schedule, not ours.
2. **They do not solve the mobile problem.** The gateway binds to loopback.
   Reaching it from a phone means binding off-box or tunneling — the
   anti-pattern that exposed 21,000+ OpenClaw gateways to the public internet
   in January 2026. The architecture's best property is that there is no
   inbound path to the desktop (spec §17).
3. **Policy will eat it.** Enterprise deployments run the conformance auditor
   and Intune policies; an unsanctioned control-plane client is exactly what
   those exist to catch.
4. **Microsoft's control plane is the product.** The runtime is free;
   identity, governance, and distribution are the paid layer. Betting on
   unexposed substrate features is betting against the vendor's business
   model.

## 4. Consequences for the current architecture

- **Concrete install paths** (previously "re-validate at build time",
  spec §3): skills install to `~/.copilot/skills/` (per-machine) or
  `~/.copilot/m-skills/` (cloud-synced workspace tier — a useful distribution
  path: the Lantern `SKILL.md` follows the user across machines); the MCP
  bridge registers in `~/.copilot/mcp-config.json`. Paths are preview-era and
  must be re-checked per release.
- **Phase 0, week-one check** (spec §19): confirm the target tenant has not
  policy-disabled Heartbeat/Automations or blocked local stdio MCP servers.
  Without those two, the bridge has no pulse; that is an admin conversation,
  not a code problem.
- **The OneDrive App Folder mailbox stays the transport** for the reasons
  spec §4 and §17 give: durable, user-owned, least-privilege, and no inbound
  path to the desktop. Nothing found in this investigation weakens that.

## 5. Adoption roadmap: what we do as Microsoft documents more

The design spec's §20 invariant is the contract for this section: the
domain-level interfaces (status, activity, remote command, response, artifact,
usage) and the `contracts/` schemas stay put; transports and integration
points swap underneath them. Each stage below is pre-agreed: when the trigger
fires, we execute the stage, and only the listed components change.

Every stage shares three preconditions, deliberately strict:

- **Documented** on Microsoft Learn (or an equivalent supported channel) as a
  supported Scout surface — not a GitHub issue, a conference demo, or upstream
  OpenClaw docs. Upstream capability is evidence of direction, never a
  trigger.
- **Policy-visible**: the surface has corresponding admin controls (Intune/
  Group Policy), so enterprise deployments can govern what we ship.
- **Security-reviewed**: the stage's threat-model delta (noted per stage) is
  written into spec §11 before code lands.

### Stage A — Native Agent Plugins installation

- **Trigger.** Scout documents installing Agent Plugins 1.0 packages
  directly.
- **What changes.** Distribution only. Runbook steps 2–3 (spec §21.1)
  collapse to "load the plugin directory"; the conformant client provides
  `PLUGIN_ROOT`/`PLUGIN_DATA` and launches the typed stdio server itself.
- **What does not.** The bridge contract, the protocol, the mobile app.
- **Threat-model delta.** None; same components, supported loading path.
- **Already prepared.** The plugin is a conformant Agent Plugins 1.0.0
  package today (`plugin/lantern-plugin/README.md`); this stage is why.

### Stage B — Supported local event/callback API for skills or MCP

- **Trigger.** Scout documents a way for a skill or local MCP server to
  receive events (file/inbox changes, task lifecycle) or to be invoked
  on-event rather than on-heartbeat.
- **What changes.** The desktop bridge only. The bridge processes commands
  on-event instead of on-heartbeat-tick: fresher `status.json`, faster
  acknowledgements, lower Work IQ overhead per tick. The heartbeat remains as
  the liveness fallback and the presence signal.
- **What does not.** The protocol, the App Folder layout, the mobile app.
  Mobile latency still improves (bridge-side pickup time shrinks toward
  zero), without touching the phone.
- **Threat-model delta.** Minor: event-driven execution must respect the same
  inbox gate (strict schema validation, expiry, receipts) — restate, don't
  redesign.

### Stage C — Supported webhook/change-notification surface

- **Trigger.** Scout (or Graph, for the App Folder) documents supported
  change notifications a small relay may subscribe to. Graph change
  notifications on the App Folder already partially satisfy this; spec §17.1
  (Mode B) is the pre-designed consumer.
- **What changes.** Deployment Mode B activates: minimal Azure Function,
  Graph subscription, content-free FCM push ("something changed, sync").
  Phone-side latency collapses to seconds.
- **What does not.** The protocol (the push is content-free by design), the
  bridge, the approval model. The in-flight watch (§16.2) remains as the
  fallback for dropped pushes.
- **Threat-model delta.** As §17.1 records: the push service must hold no
  protocol content and no user Graph tokens beyond the subscription's needs;
  its compromise degrades latency only.

### Stage D — Supported gateway/operator API for third-party clients

- **Trigger.** Microsoft documents a supported way for a local third-party
  process to talk to Scout's gateway (status, sessions, events) with
  authentication and admin policy controls.
- **What changes.** The desktop bridge's *inward* face. The MCP helper reads
  live agent state through the supported API instead of inferring it at
  skill-orchestration time: real-time `state`/`currentTask` in `status.json`,
  richer activity events, possibly reliable `cancel_request` handling
  (upstream OpenClaw already models session cancellation).
- **What does not.** The protocol and the mobile app. The bridge remains the
  single writer of bridge-authored files; the gateway API is an input, never
  a bypass.
- **Threat-model delta.** Moderate: a new local trust relationship
  (bridge ↔ gateway). Device/token handling for the gateway credential goes
  in OS-protected storage next to the MSAL cache rules (spec §10); nothing
  from the gateway API is ever written into protocol files verbatim without
  schema validation.

### Stage E — Supported remote nodes / first-party mobile API

- **Trigger.** Microsoft documents phone-class access: OpenClaw-style paired
  nodes for Scout, or a first-party Scout mobile/session API (cloud-brokered,
  not desktop-inbound).
- **What changes.** The transport adapter. `GraphBridgeTransport` gains a
  sibling (`ScoutNodeTransport` or similar) behind the same
  `BridgeTransport` port; the app chooses per capability handshake. Command
  latency collapses from minutes to seconds end-to-end.
- **What does not.** The domain model, the application services, the screens,
  the contract tests — this is the §20 invariant's designed payoff. The
  OneDrive mailbox remains as the durable offline path (a direct channel is
  ephemeral; the mailbox survives Scout being offline for hours), unless the
  documented surface itself provides durable queuing.
- **Threat-model delta.** Significant and pre-scoped: device enrollment,
  credential storage on the phone, and the "no inbound path to the desktop"
  property must be preserved (acceptable only if Microsoft's surface is
  cloud-brokered or outbound-only from the desktop). Full §11 review before
  any code.

### Stage F — Supported remote approval API

- **Trigger.** Microsoft exposes an explicit, supported, policy-governed
  remote approval surface for Scout's sensitive actions (spec §20 already
  lists this as its own open question).
- **What changes.** The product boundary itself — the only stage that does.
  `remoteApprovals` flips from `const: false` to a negotiated capability; a
  new approval flow ships in the app.
- **What does not.** Everything else; approvals arrive as a new capability
  and payload type under the §15.2 evolution rules (bridge-first,
  capability-gated).
- **Threat-model delta.** The largest of any stage. This is a deliberate
  *product* decision, not just an engineering one: it requires its own
  decision record, a full threat-model revision, and probably device-bound
  command signing (spec §11.2) as a prerequisite rather than an option.

### Watch-only: substrate signals that inform but never trigger

- **Upstream OpenClaw releases** (gateway protocol, node host work, channel
  plugins): direction signals for what Microsoft may productize next. We
  track them to *prepare* stages, never to act on them.
- **Microsoft's upstream policy-conformance contributions**: worth aligning
  with early — the bridge's audit events (refused commands, expiry
  dispositions, receipts) should stay shaped so a conformance auditor reads
  them as evidence of good behavior, not anomalies.
- **Scout's cloud-synced workspace skills tier**: already useful (it
  distributes our `SKILL.md` across the user's machines); if Microsoft
  documents sync semantics further, revisit whether plugin updates can ride
  it.

## 6. What will not change this decision

- Substrate features that are merely *present* (a reachable loopback port, a
  config file that accepts channel entries, an unofficial pairing flow).
  Physical presence is not a contract.
- Community workarounds, reverse-engineering writeups, or "it works on my
  machine" demos against the Scout build.
- Upstream OpenClaw documenting a surface that Scout has not adopted.
- Preview-tier Microsoft features without admin policy controls: if an admin
  cannot govern it, an enterprise cannot deploy it, and we do not build the
  MVP's spine on it.

## 7. Monitoring and review

- **Sources watched:** Microsoft Learn (Scout docs and release notes), the
  M365 Message Center, the Microsoft 365 blog, Scout's Intune ADMX policy
  schema (new policy names reveal new surfaces early), and upstream
  OpenClaw release notes.
- **Cadence:** every phase boundary (spec §19) includes a re-validation
  checkpoint against this record; additionally, any Scout release note that
  mentions extensibility, gateway, nodes, webhooks, or approvals gets checked
  against the stage triggers within the same working cycle.
- **Recording:** when a trigger fires, update this record's revision history,
  update spec §3/§20 in the same change, and execute the stage as its own
  PR with the stage letter in the title.

## Revision history

| Date | Change |
|---|---|
| 15 August 2026 | Initial decision (skills + MCP only) with findings as of Scout preview, June–August 2026. |
| 15 August 2026 | Expanded into forward-looking adoption roadmap (Stages A–F, watch-only signals, monitoring plan). |

## Sources

- [Introducing Microsoft Scout (Microsoft 365 blog, 2 June 2026)](https://www.microsoft.com/en-us/microsoft-365/blog/2026/06/02/introducing-microsoft-scout-your-always-on-personal-agent/)
- [Use Microsoft Scout (Microsoft Learn)](https://learn.microsoft.com/en-us/microsoft-scout/use-microsoft-scout)
- [Manage Microsoft Scout with Group Policy / Intune (Microsoft Learn)](https://learn.microsoft.com/en-us/microsoft-scout/manage-group-policy)
- [Microsoft launches Scout (TechCrunch, 2 June 2026)](https://techcrunch.com/2026/06/02/microsoft-launches-scout-an-openclaw-inspired-personal-assistant/)
- [Microsoft just made the agent runtime free (The New Stack, 7 June 2026)](https://thenewstack.io/microsoft-scout-openclaw-runtime/)
- [Scout runs on OpenClaw (Till Freitag)](https://till-freitag.com/en/blog/microsoft-scout-openclaw-gateway-en)
- [OpenClaw gateway architecture (upstream docs)](https://github.com/openclaw/openclaw/blob/main/docs/concepts/architecture.md)
