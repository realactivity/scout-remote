# lantern-plugin

The Lantern desktop bridge, packaged as a conformant
**[Agent Plugins 1.0.0](https://agent-plugins.org)** plugin (specification
published 6 August 2026). Written as a showcase of the standard used well.
Identity lives in the manifest. Components live at fixed paths. The transport
is typed. Plugin variables hold persistent state. Every choice, including the
features deliberately left unused, has a documented reason.

## Anatomy

```
lantern-plugin/
├── plugin.json                       Identity and metadata only.
│                                     The spec forbids declaring or relocating
│                                     components from the manifest (§5, §6).
├── mcp.json                          One stdio MCP server, explicitly typed (§7.2)
├── CHANGELOG.md                      SemVer history (§10.2; standard layout, §4.2)
├── LICENSE                           MIT (standard layout, §4.2; matches the manifest's SPDX field)
├── skills/
│   └── lantern-bridge/          Discovered as an immediate child of skills/ (§7.1)
│       ├── SKILL.md                  Orchestration instructions. Short, policy-focused.
│       └── references/
│           └── protocol-quick-reference.md
└── bridge/
    └── Lantern.Bridge/           MCP helper source (C#). Desktop MSAL, Graph, protocol I/O.
                                      publish/ output is built at package time, never committed.
```

## How this package uses the standard

| Spec feature | § | Use here |
|---|---|---|
| Closed manifest | §5 | `plugin.json` carries the two required fields (`$schema`, `name`) plus every applicable metadata field (`version`, `description`, `license`, `author`, `homepage`, `repository`, `keywords`). Nothing else. The schema is closed, and components cannot be declared inline. |
| Fixed-path discovery | §6 | Skills come from `skills/`. MCP config comes from root `mcp.json`. There is nothing to configure and no discovery order to learn. The file structure *is* the contract. |
| Agent Skills packaging | §7.1 | One skill, `lantern-bridge`, frontmatter per [agentskills.io](https://agentskills.io/specification). Operational detail (protocol layout, state machine, disposition rules) lives in `references/`, keeping `SKILL.md` short while the agent loads depth only when needed. |
| Typed MCP transport | §7.2 | `"type": "stdio"` is explicit. A client never infers the transport from config shape. |
| Executable resolution | §7.2.1 | `"command": "dotnet"` is a single bare executable token resolved by platform search. This is a deliberate trade-off. The bridge ships one framework-dependent dll for all platforms instead of per-OS self-contained binaries. If this package ever bundles a native executable, the spec requires a plugin-relative `./` command instead. |
| Default working directory | §7.2.1 | `cwd` is omitted, so a conformant client uses the plugin root. Args use absolute `${PLUGIN_ROOT}` paths, so the working directory never matters. |
| Plugin variables | §9 | `${PLUGIN_ROOT}` anchors the dll path portably. `${PLUGIN_DATA}/state` gives the bridge a client-managed writable directory that **survives plugin updates**. It holds delta cursors, processed-receipt mirrors, and diagnostics. MSAL token caches never go there. OS-protected storage only (design spec §10). |
| No secrets in config | §9.2 | `env` carries an agent id and a state path. Nothing sensitive. This mirrors the bridge protocol's own rule. No secrets in any protocol file, ever. |
| Failure isolation | §7.2.2, §11.3 | The skill and the MCP server fail independently. A client that cannot start the bridge still loads the skill (which then reports the bridge as unavailable). A skills-only client simply ignores `mcp.json`. |
| SemVer and changelog | §10.2, §4.2 | `version` follows SemVer. `CHANGELOG.md` sits at the plugin root per the standard layout. |
| Client extensions | §8 | **Deliberately unused.** Extension namespaces belong to *clients*, and Scout has not published one. When it does, Scout-specific settings land under `"extensions"` keyed by its real namespace, and/or a top-level namespace directory, without touching any portable component. Restraint here is conformance. Inventing a namespace nobody implements is noise, not portability. |

## Conformance

`plugin.json` and `mcp.json` validate against the official schemas (checked
15 August 2026).

```bash
curl -sO https://agent-plugins.org/schemas/1.0.0/plugin.schema.json
curl -sO https://agent-plugins.org/schemas/1.0.0/mcp.schema.json
# any JSON Schema draft 2020-12 validator
python3 -c "import json, jsonschema; jsonschema.validate(json.load(open('plugin.json')), json.load(open('plugin.schema.json')))"
```

The package ships the standard-layout `LICENSE` file (MIT) together with the
matching SPDX `license` field in `plugin.json`, so the license travels with the
plugin even when it is distributed separately from the repository (which
carries the same license at its root).

## Installing (until Scout is an Agent Plugins client)

The package is the portable source of truth. Scout is not yet assumed to load
Agent Plugins natively, so the components are installed through Scout's
supported mechanisms.

1. **Publish the bridge** so the server starts from a prebuilt binary (end
   users need only the .NET runtime).

   ```bash
   dotnet restore bridge/Lantern.Bridge --locked-mode
   dotnet publish bridge/Lantern.Bridge -c Release --no-restore -o bridge/publish
   ```

2. **Install the skill.** Place `skills/lantern-bridge/` (including
   `references/`) into a supported Scout skills directory — currently
   `~/.copilot/skills/` (per-machine) or `~/.copilot/m-skills/` (cloud-synced
   across the user's machines). Preview-era paths; re-check per release
   (`docs/scout-openclaw-substrate.md` in the source repository).
3. **Register the MCP server** through Scout's supported MCP configuration
   path (currently `~/.copilot/mcp-config.json`), using `mcp.json` as the
   shape. Outside a conformant Agent Plugins
   client, `${PLUGIN_ROOT}` and `${PLUGIN_DATA}` are not expanded. Substitute
   the plugin directory yourself. The bridge falls back to the platform
   app-data directory when `LANTERN_STATE_DIR` is unset or unexpanded.
4. **Sign in once.** The bridge establishes its own MSAL cache (same Entra app
   ID and Microsoft 365 user as the mobile app, its **own** token cache).
   Tokens are never copied between devices. The cache requires DPAPI, macOS
   Keychain, or Linux Keyring; the Graph backend fails closed when protected
   storage is unavailable.
5. **Enable the heartbeat/automation** with an acceptable cadence policy.

On Windows, steps 2 and 3 are scripted by
`scripts/install-scout-bridge.ps1`. Quit Scout, then:

```powershell
.\scripts\install-scout-bridge.ps1 -ClientId $env:LANTERN_CLIENT_ID -TenantId $env:LANTERN_TENANT_ID
```

The script writes `~/.copilot/mcp-config.json` and
`~/.copilot/m-mcp-servers.json` using Copilot/Scout `type: local`. It cannot
enable the server in Scout's Permissions UI. After it finishes, start Scout,
open a new chat, and enable `lantern-bridge` under Manage Permissions.

The moment Scout ships as a compatible client, steps 2 and 3 collapse to
"load the plugin directory". A conformant client reads `plugin.json`,
discovers the skill, and launches the typed stdio server with `PLUGIN_ROOT`
and `PLUGIN_DATA` provided. That loading contract is the entire standard.

## Division of responsibility

| Component | Owns |
|---|---|
| `SKILL.md` and `references/` | When to poll, how to interpret *validated* bridge state, response formatting, when to stop and ask for desktop attention. Enforces the no-remote-approvals and prompt-injection rules at the orchestration layer. |
| `Lantern.Bridge` (MCP) | Desktop MSAL sign-in, Graph App Folder I/O, schema and version checks, idempotency receipts, protocol serialization, state under `LANTERN_STATE_DIR`. Deterministic. Returns no secrets to Scout. |

## Bridge tools and harness

| Tool | Behavior |
|---|---|
| `publish_status` | Writes `agents/{agentId}/status.json` with `lastSeenUtc`. |
| `read_inbox` | Returns only schema-valid, unexpired, unreceipted commands. Invalid files get an audit event. |
| `write_acknowledgement` | First wire status (`acknowledged`). |
| `write_response` | Schema-validated write; terminal responses are immutable. |
| `record_processed_receipt` | Exactly-once receipt. |
| `read_activity` | Activity events by day folder, optional `sinceUtc`; defaults to the 30-day retention window. |
| `resolve_artifact` | Graph item lookup, or filesystem stand-in. |

```bash
# Offline inbox processor (CI / local harness)
LANTERN_BACKEND=filesystem \
LANTERN_APPROOT=/tmp/lantern-approot \
LANTERN_STATE_DIR=/tmp/lantern-state \
dotnet plugin/lantern-plugin/bridge/publish/Lantern.Bridge.dll --once
```

| Variable | Meaning |
|---|---|
| `LANTERN_BACKEND` | `filesystem` or `graph`. Inferred as filesystem when `LANTERN_APPROOT` is set. |
| `LANTERN_APPROOT` | Local directory laid out like the App Folder. Required for filesystem. |
| `LANTERN_STATE_DIR` | Receipts mirror and diagnostics. Unset or unexpanded falls back to platform app-data. |
| `LANTERN_CLIENT_ID` / `LANTERN_TENANT_ID` | Graph / MSAL only. |
| `LANTERN_USE_DEVICE_CODE` | Device-code sign-in is the headless default. Set `0` only for an interactive desktop browser flow. |

Phase 0 proof (automation vs tenant) is in [`docs/phase0-report.md`](../../docs/phase0-report.md).
