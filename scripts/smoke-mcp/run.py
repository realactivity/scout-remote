#!/usr/bin/env python3
"""Optional MCP stdio helper for humans without the .NET test host.

CI uses Lantern.Bridge.Tests.McpStdioSmokeTests. This script exercises the
same newline-delimited JSON-RPC transport, seeds one inbox command, lists
tools, invokes publish_status and read_inbox, and scans for credential shapes.
"""

from __future__ import annotations

import json
import os
import queue
import subprocess
import sys
import tempfile
import threading
from datetime import datetime, timedelta, timezone
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
    assert proc.stdin is not None
    proc.stdin.write(json.dumps(payload).encode("utf-8") + b"\n")
    proc.stdin.flush()


def read_message(proc: subprocess.Popen[bytes], timeout_seconds: float = 10) -> dict:
    assert proc.stdout is not None
    result: queue.Queue[bytes] = queue.Queue(maxsize=1)
    threading.Thread(target=lambda: result.put(proc.stdout.readline()), daemon=True).start()
    try:
        line = result.get(timeout=timeout_seconds)
    except queue.Empty:
        raise SystemExit(f"MCP server did not respond within {timeout_seconds:g} seconds")
    if not line:
        raise SystemExit("MCP server closed stdout")
    return json.loads(line)


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
    command_id = "01KZYMGHG0NPD8AF5A96MP2YFW"
    now = datetime.now(timezone.utc).replace(microsecond=0)
    inbox = approot / "commands" / "inbox"
    inbox.mkdir(parents=True)
    (inbox / f"{command_id}.json").write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "commandId": command_id,
                "conversationId": "remote-mcp-smoke",
                "type": "message",
                "createdUtc": now.isoformat().replace("+00:00", "Z"),
                "expiresUtc": (now + timedelta(hours=4)).isoformat().replace("+00:00", "Z"),
                "requestedBy": {
                    "entraObjectId": "00000000-0000-0000-0000-000000000000"
                },
                "text": "seeded MCP smoke command",
            }
        )
    )
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
                "clientInfo": {"name": "lantern-smoke", "version": "0.2.1"},
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
        if command_id not in inbox_text:
            print("read_inbox did not return the seeded command")
            return 1
        if "eyJ" in inbox_text or "client_secret" in inbox_text:
            print("inbox result contained credential-shaped material")
            return 1
        forbidden = ("access_token", "refresh_token", "client_secret", "Bearer ", "eyJ")
        for path in approot.rglob("*"):
            if not path.is_file():
                continue
            contents = path.read_text(errors="replace")
            if any(marker.lower() in contents.lower() for marker in forbidden):
                print(f"credential-shaped material found in {path.relative_to(approot)}")
                return 1
        print("MCP stdio smoke: PASS")
        print("tools:", ", ".join(sorted(names)))
        return 0
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=5)


if __name__ == "__main__":
    sys.exit(main())
