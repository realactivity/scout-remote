#!/usr/bin/env python3
"""Optional MCP stdio helper for humans without the .NET test host.

CI uses Lantern.Bridge.Tests.McpStdioSmokeTests. This script is the same
assertions over Content-Length JSON-RPC: initialize, list tools, invoke
publish_status and read_inbox, and refuse credential-shaped payloads.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path


def find_bridge() -> Path:
    here = Path(__file__).resolve()
    for parent in [here, *here.parents]:
        for candidate in (
            parent / "plugin/lantern-plugin/bridge/Lantern.Bridge/bin/Release/net10.0/Lantern.Bridge.dll",
            parent / "plugin/lantern-plugin/bridge/publish/Lantern.Bridge.dll",
        ):
            if candidate.is_file():
                return candidate
    raise SystemExit("Lantern.Bridge.dll not found. Build Lantern.sln -c Release first.")


def write_message(proc: subprocess.Popen[bytes], payload: dict) -> None:
    body = json.dumps(payload).encode("utf-8")
    header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
    assert proc.stdin is not None
    proc.stdin.write(header + body)
    proc.stdin.flush()


def read_message(proc: subprocess.Popen[bytes]) -> dict:
    assert proc.stdout is not None
    headers: dict[str, str] = {}
    while True:
        line = proc.stdout.readline()
        if not line:
            err = proc.stderr.read() if proc.stderr else b""
            raise SystemExit(f"MCP server closed stdout. stderr={err.decode('utf-8', 'replace')}")
        if line in (b"\r\n", b"\n"):
            break
        decoded = line.decode("ascii", "replace")
        key, _, value = decoded.partition(":")
        headers[key.strip().lower()] = value.strip()
    length = int(headers["content-length"])
    body = proc.stdout.read(length)
    return json.loads(body)


def rpc(proc: subprocess.Popen[bytes], method: str, params: dict | None, req_id: int) -> dict:
    message: dict = {"jsonrpc": "2.0", "id": req_id, "method": method}
    if params is not None:
        message["params"] = params
    write_message(proc, message)
    while True:
        reply = read_message(proc)
        if reply.get("id") == req_id:
            return reply


def main() -> int:
    dll = find_bridge()
    root = Path(tempfile.mkdtemp(prefix="lantern-mcp-"))
    approot = root / "approot"
    state = root / "state"
    approot.mkdir()
    state.mkdir()
    env = os.environ.copy()
    env.update(
        {
            "LANTERN_BACKEND": "filesystem",
            "LANTERN_APPROOT": str(approot),
            "LANTERN_STATE_DIR": str(state),
            "LANTERN_AGENT_ID": "primary",
        }
    )
    proc = subprocess.Popen(
        ["dotnet", str(dll)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
    )
    try:
        init = rpc(
            proc,
            "initialize",
            {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "lantern-smoke", "version": "0.2.0"},
            },
            1,
        )
        if "result" not in init:
            print(init)
            return 1
        write_message(proc, {"jsonrpc": "2.0", "method": "notifications/initialized"})
        tools = rpc(proc, "tools/list", {}, 2)
        names = [tool["name"] for tool in tools["result"]["tools"]]
        required = {
            "publish_status",
            "read_inbox",
            "write_acknowledgement",
            "write_response",
            "record_processed_receipt",
            "read_activity",
            "resolve_artifact",
        }
        missing = required - set(names)
        if missing:
            print(f"missing tools: {missing}")
            return 1
        status = rpc(proc, "tools/call", {"name": "publish_status", "arguments": {"state": "idle"}}, 3)
        text = json.dumps(status)
        if "access_token" in text or "refresh_token" in text or "Bearer" in text:
            print("tool result leaked credential material")
            return 1
        inbox = rpc(proc, "tools/call", {"name": "read_inbox", "arguments": {}}, 4)
        inbox_text = json.dumps(inbox)
        if "eyJ" in inbox_text:
            print("inbox result looked like a JWT")
            return 1
        print("MCP stdio smoke: PASS")
        print("tools:", ", ".join(sorted(names)))
        return 0
    finally:
        proc.kill()
        proc.wait(timeout=5)


if __name__ == "__main__":
    sys.exit(main())
