# MCP stdio helper

Language-independent client for the same assertions as
`McpStdioSmokeTests`. CI still runs the .NET test. Use this when you want
to exercise the published dll without the test host.

```bash
dotnet restore plugin/lantern-plugin/bridge/Lantern.Bridge --locked-mode
dotnet build plugin/lantern-plugin/bridge/Lantern.Bridge -c Release --no-restore
python3 scripts/smoke-mcp/run.py
```

The script starts `Lantern.Bridge` on stdio against a temp filesystem
approot, uses newline-delimited JSON-RPC, seeds one command, lists the seven
Phase 0 tools, calls `publish_status` and `read_inbox`, and fails on a timeout
or credential-shaped payload.
