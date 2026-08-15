# scout-remote-plugin

Desktop bridge packaged as an **Agent Plugins 1.0** bundle (spec §12): the Scout skill and its local MCP bridge/helper travel together as one portable directory.

```
scout-remote-plugin/
├── plugin.json                       Package identity (Agent Plugins 1.0.0 manifest; components
│                                     are discovered at the fixed paths below, never declared)
├── mcp.json                          Local MCP bridge server declaration (stdio, ${PLUGIN_ROOT})
├── skills/
│   └── scout-remote-bridge/
│       └── SKILL.md                  Agent-facing orchestration instructions
└── bridge/
    └── ScoutRemote.Bridge/           MCP helper: desktop MSAL + Graph App Folder + protocol I/O
```

## Packaging rule (spec §12)

This package is the **portable source-of-truth**. Scout is *not* assumed to install Agent Plugins packages natively today, so for current deployments:

1. Publish the bridge so the MCP server starts from a prebuilt binary (no SDK or compile-on-startup on the user's machine — only the .NET runtime):

   ```bash
   dotnet publish bridge/ScoutRemote.Bridge -c Release -o bridge/publish
   ```

   `mcp.json` launches `bridge/publish/ScoutRemote.Bridge.dll`. The `publish/` output ships inside the distributed package; it is never committed to the repo.
2. Place `skills/scout-remote-bridge/SKILL.md` into a supported Scout skills directory.
3. Register the published bridge as a local MCP server through Scout's supported MCP configuration path (see `mcp.json` for the shape; adapt to Scout's actual config format).
4. Run the bridge sign-in once so it establishes its own MSAL cache — the same Entra app ID and signed-in Microsoft 365 user as the mobile app, but its **own** token cache. Tokens are never copied between devices.
5. Enable the bridge heartbeat/automation with an acceptable cadence policy.

If Scout later documents native Agent Plugins installation, this same package becomes directly installable without changing the bridge contract.

**Conformance (spec Appendix C checkpoint, validated 15 Aug 2026):** `plugin.json` and `mcp.json` validate against the official Agent Plugins 1.0.0 schemas (published 6 Aug 2026, [agent-plugins.org](https://agent-plugins.org)). Per the standard, components live at fixed paths — skills are discovered under `skills/`, MCP servers come from the root `mcp.json` (explicit `"type"` per entry, `${PLUGIN_ROOT}` for plugin-relative paths) — and the manifest declares identity only; it cannot relocate or inline components. Re-validate against Scout's installer requirements if/when Scout ships native plugin installation.

## Division of responsibility

| Component | Owns |
|---|---|
| `SKILL.md` | When to poll, how to interpret *validated* bridge state, response formatting, when to stop and ask for desktop attention. Enforces the no-remote-approvals and prompt-injection rules at the orchestration layer. |
| `ScoutRemote.Bridge` (MCP) | Desktop MSAL sign-in, Graph App Folder I/O, schema/version checks, idempotency receipts, protocol serialization. Deterministic; returns no secrets to Scout. |
