# scout-remote-plugin

The Scout Remote desktop bridge, packaged as a conformant **[Agent Plugins 1.0.0](https://agent-plugins.org)** plugin (specification published 6 August 2026) — and written to be a showcase of the standard used well: identity in the manifest, components at fixed paths, a typed transport, plugin variables for persistent state, and a documented reason for every choice, including the features deliberately left unused.

## Anatomy

```
scout-remote-plugin/
├── plugin.json                       Identity + metadata only — the spec forbids declaring
│                                     or relocating components from the manifest (§5, §6)
├── mcp.json                          One stdio MCP server, explicitly typed (§7.2)
├── CHANGELOG.md                      SemVer history (§10.2; standard layout, §4.2)
├── skills/
│   └── scout-remote-bridge/          Discovered as an immediate child of skills/ (§7.1)
│       ├── SKILL.md                  Orchestration instructions — short, policy-focused
│       └── references/
│           └── protocol-quick-reference.md   Detail the agent reads on demand
└── bridge/
    └── ScoutRemote.Bridge/           MCP helper source (C#): desktop MSAL + Graph + protocol I/O
        └── … publish/ output is built at package time, never committed
```

## How this package uses the standard

| Spec feature | § | Use here |
|---|---|---|
| Closed manifest | §5 | `plugin.json` carries the two required fields (`$schema`, `name`) plus every applicable metadata field: `version`, `description`, `author`, `homepage`, `repository`, `keywords`. Nothing else — the schema is closed, and components cannot be declared inline. |
| Fixed-path discovery | §6 | Skills come from `skills/`, MCP config from root `mcp.json`. There is nothing to configure and no discovery order to learn — the file structure *is* the contract. |
| Agent Skills packaging | §7.1 | One skill, `scout-remote-bridge`, frontmatter per [agentskills.io](https://agentskills.io/specification). Operational detail (protocol layout, state machine, disposition rules) lives in `references/`, keeping `SKILL.md` short while the agent loads depth only when needed. |
| Typed MCP transport | §7.2 | `"type": "stdio"` is explicit. A client never infers the transport from config shape. |
| Executable resolution | §7.2.1 | `"command": "dotnet"` is a single bare executable token resolved by platform search — a deliberate trade-off: the bridge ships one framework-dependent dll for all platforms instead of per-OS self-contained binaries. If this package ever bundles a native executable, the spec requires a plugin-relative `./` command instead. |
| Default working directory | §7.2.1 | `cwd` is omitted, so a conformant client uses the plugin root. Args use absolute `${PLUGIN_ROOT}` paths, so the working directory never matters. |
| Plugin variables | §9 | `${PLUGIN_ROOT}` anchors the dll path portably. `${PLUGIN_DATA}/state` gives the bridge a client-managed writable directory that **survives plugin updates** — used for delta cursors, processed-receipt mirrors, and diagnostics. MSAL token caches never go there: OS-protected storage only (design spec §10). |
| No secrets in config | §9.2 | `env` carries an agent id and a state path — nothing sensitive. This mirrors the bridge protocol's own rule: no secrets in any protocol file, ever. |
| Failure isolation | §7.2.2, §11.3 | The skill and the MCP server fail independently. A client that can't start the bridge still loads the skill (which then reports the bridge as unavailable); a skills-only client simply ignores `mcp.json`. |
| SemVer + changelog | §10.2, §4.2 | `version` follows SemVer; `CHANGELOG.md` sits at the plugin root per the standard layout. |
| Client extensions | §8 | **Deliberately unused.** Extension namespaces belong to *clients*, and Scout has not published one. When it does, Scout-specific settings land under `"extensions": { "com.microsoft.scout": { … } }` (or its real namespace) and/or a top-level namespace directory — without touching any portable component. Restraint here is conformance: inventing a namespace nobody implements is noise, not portability. |

## Conformance

`plugin.json` and `mcp.json` validate against the official schemas (checked 15 August 2026):

```bash
curl -sO https://agent-plugins.org/schemas/1.0.0/plugin.schema.json
curl -sO https://agent-plugins.org/schemas/1.0.0/mcp.schema.json
# validate with any JSON Schema draft 2020-12 validator, e.g.:
python3 -c "import json, jsonschema; jsonschema.validate(json.load(open('plugin.json')), json.load(open('plugin.schema.json')))"
```

Known gap: the standard layout includes a `LICENSE` file and the manifest supports an SPDX `license` field; this repository has not chosen a license yet. Add both together when it does.

## Installing (until Scout is an Agent Plugins client)

The package is the portable source-of-truth. Scout is not yet assumed to load Agent Plugins natively, so the components are installed through Scout's supported mechanisms:

1. **Publish the bridge** so the server starts from a prebuilt binary (end users need only the .NET runtime):

   ```bash
   dotnet publish bridge/ScoutRemote.Bridge -c Release -o bridge/publish
   ```

2. **Install the skill** — place `skills/scout-remote-bridge/` (including `references/`) into a supported Scout skills directory.
3. **Register the MCP server** through Scout's supported MCP configuration path, using `mcp.json` as the shape. Outside a conformant Agent Plugins client, `${PLUGIN_ROOT}`/`${PLUGIN_DATA}` are not expanded — substitute the plugin directory yourself; the bridge falls back to the platform app-data directory when `SCOUT_REMOTE_STATE_DIR` is unset or unexpanded.
4. **Sign in once** — the bridge establishes its own MSAL cache (same Entra app ID and Microsoft 365 user as the mobile app, its **own** token cache; tokens are never copied between devices).
5. **Enable the heartbeat/automation** with an acceptable cadence policy.

The moment Scout ships as a compatible client, steps 2–3 collapse to "load the plugin directory": a conformant client reads `plugin.json`, discovers the skill, launches the typed stdio server with `PLUGIN_ROOT`/`PLUGIN_DATA` provided — that loading contract is the entire standard.

## Division of responsibility

| Component | Owns |
|---|---|
| `SKILL.md` (+ `references/`) | When to poll, how to interpret *validated* bridge state, response formatting, when to stop and ask for desktop attention. Enforces the no-remote-approvals and prompt-injection rules at the orchestration layer. |
| `ScoutRemote.Bridge` (MCP) | Desktop MSAL sign-in, Graph App Folder I/O, schema/version checks, idempotency receipts, protocol serialization, state under `SCOUT_REMOTE_STATE_DIR`. Deterministic; returns no secrets to Scout. |
