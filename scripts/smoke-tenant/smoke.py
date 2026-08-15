#!/usr/bin/env python3
"""Human-gated Graph App Folder round trip (spec §19, §21.1).

Reads config from the environment only. Writes local artifacts under
scripts/smoke-tenant/.local/ (NEVER-COMMIT).
"""

from __future__ import annotations

import json
import os
import sys
import time
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

LOCAL = Path(__file__).resolve().parent / ".local"
LOCAL.mkdir(parents=True, exist_ok=True)


def fail(message: str) -> int:
    print(f"FAIL: {message}")
    return 1


def new_ulid() -> str:
    alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    value = int.from_bytes(
        int(time.time() * 1000).to_bytes(6, "big") + os.urandom(10),
        "big",
    )
    encoded = []
    for _ in range(26):
        encoded.append(alphabet[value & 31])
        value >>= 5
    return "".join(reversed(encoded))


def main() -> int:
    try:
        import msal
        import requests
    except ImportError:
        return fail("install dependencies: python3 -m pip install --user msal requests")

    client_id = os.environ.get("LANTERN_CLIENT_ID", "").strip()
    tenant_id = os.environ.get("LANTERN_TENANT_ID", "").strip()
    if not client_id or not tenant_id:
        return fail("set LANTERN_CLIENT_ID and LANTERN_TENANT_ID")

    authority = f"https://login.microsoftonline.com/{tenant_id}"
    scopes = ["Files.ReadWrite.AppFolder"]
    cache_path = LOCAL / "msal_cache.bin"
    cache = msal.SerializableTokenCache()
    if cache_path.exists():
        cache.deserialize(cache_path.read_text())

    app = msal.PublicClientApplication(client_id, authority=authority, token_cache=cache)
    result = None
    accounts = app.get_accounts()
    if accounts:
        result = app.acquire_token_silent(scopes, account=accounts[0])
    if not result:
        flow = app.initiate_device_flow(scopes=scopes)
        if "user_code" not in flow:
            return fail(f"device flow failed: {flow}")
        print(flow["message"])
        result = app.acquire_token_by_device_flow(flow)

    if cache.has_state_changed:
        cache_path.write_text(cache.serialize())
        os.chmod(cache_path, 0o600)

    if "access_token" not in result:
        return fail(f"sign-in failed: {result.get('error_description', result)}")

    token = result["access_token"]
    headers = {"Authorization": f"Bearer {token}"}
    graph = "https://graph.microsoft.com/v1.0"

    checklist: list[tuple[str, str]] = []

    approot = graph_request("GET", f"{graph}/me/drive/special/approot", headers=headers)
    if approot.status_code >= 400:
        checklist.append(("Files.ReadWrite.AppFolder / approot resolve", "FAIL"))
        print(approot.text)
        print_checklist(checklist)
        return 1
    checklist.append(("Files.ReadWrite.AppFolder / approot resolve", "PASS"))
    item = approot.json()
    print(f"approot id={item.get('id')} name={item.get('name')}")

    protocol = {
        "protocol": "lantern",
        "version": "1.0",
        "agentId": "primary",
        "bridgeVersion": "0.2.1",
        "capabilities": [
            "status.read",
            "activity.read",
            "message.send",
            "summary.request",
            "artifacts.read",
        ],
        "nativeScoutChatMirror": False,
        "remoteApprovals": False,
    }
    got = graph_request(
        "GET",
        f"{graph}/me/drive/special/approot:/protocol.json:/content",
        headers=headers,
    )
    if got.status_code == 404:
        put = graph_request(
            "PUT",
            f"{graph}/me/drive/special/approot:/protocol.json:/content",
            headers={
                **headers,
                "Content-Type": "application/json",
                "If-None-Match": "*",
            },
            data=json.dumps(protocol),
        )
        checklist.append(("desktop persona created protocol.json", "PASS" if put.ok else "FAIL"))
        got = graph_request(
            "GET",
            f"{graph}/me/drive/special/approot:/protocol.json:/content",
            headers=headers,
        )
    else:
        checklist.append(("existing protocol.json left unchanged", "PASS" if got.ok else "FAIL"))
    checklist.append(("mobile-sim persona read protocol.json", "PASS" if got.ok else "FAIL"))

    command_id = new_ulid()
    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    command = {
        "schemaVersion": 1,
        "commandId": command_id,
        "conversationId": "remote-tenant-smoke",
        "type": "request_status",
        "createdUtc": now,
        "expiresUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + 4 * 3600)),
        "requestedBy": {"entraObjectId": result.get("id_token_claims", {}).get("oid", "unknown")},
    }
    inbox = graph_request(
        "PUT",
        f"{graph}/me/drive/special/approot:/commands/inbox/{command_id}.json:/content",
        headers={
            **headers,
            "Content-Type": "application/json",
            "If-None-Match": "*",
        },
        data=json.dumps(command),
    )
    checklist.append(("mobile-sim wrote inbox command", "PASS" if inbox.ok else "FAIL"))
    inbox_read = graph_request(
        "GET",
        f"{graph}/me/drive/special/approot:/commands/inbox/{command_id}.json:/content",
        headers=headers,
    )
    checklist.append(("desktop-sim read inbox command", "PASS" if inbox_read.ok else "FAIL"))

    if os.environ.get("LANTERN_RUN_BRIDGE") == "1":
        print("LANTERN_RUN_BRIDGE=1: run the local bridge against Graph yourself:")
        print("  LANTERN_BACKEND=graph LANTERN_USE_DEVICE_CODE=1 dotnet plugin/lantern-plugin/bridge/Lantern.Bridge/bin/Release/net10.0/Lantern.Bridge.dll --once")

    checklist.append(("Scout heartbeat/automations not policy-disabled", "HUMAN"))
    checklist.append(("Local stdio MCP servers allowed by tenant policy", "HUMAN"))
    checklist.append(("Admin-consent behavior for Files.ReadWrite.AppFolder confirmed", "HUMAN"))

    if os.environ.get("LANTERN_SMOKE_CLEANUP") == "1":
        cleaned = graph_request(
            "DELETE",
            f"{graph}/me/drive/special/approot:/commands/inbox/{command_id}",
            headers=headers,
        )
        checklist.append(("smoke command cleaned up", "PASS" if cleaned.ok else "FAIL"))

    print_checklist(checklist)
    (LOCAL / "last-run.json").write_text(json.dumps({"approot": item.get("id"), "checklist": checklist}, indent=2))
    failed = [row for row in checklist if row[1] == "FAIL"]
    return 1 if failed else 0


def graph_request(method: str, url: str, **kwargs):
    """One Graph call with a single 429/503 Retry-After retry."""
    import requests

    response = requests.request(method, url, timeout=30, **kwargs)
    if response.status_code in (429, 503):
        raw = response.headers.get("Retry-After", "2")
        try:
            wait = max(int(raw), 1)
        except ValueError:
            try:
                wait = max(int((parsedate_to_datetime(raw) - datetime.now(timezone.utc)).total_seconds()), 1)
            except (TypeError, ValueError):
                wait = 2
        wait = min(wait, 30)
        time.sleep(wait)
        response = requests.request(method, url, timeout=30, **kwargs)
    return response


def print_checklist(rows: list[tuple[str, str]]) -> None:
    print()
    print("Phase 0 week-one checklist (spec §19)")
    print("------------------------------------")
    for name, status in rows:
        print(f"[{status:5}] {name}")
    print()
    print("HUMAN rows must be confirmed on the desktop / admin center.")
    print("Do not commit scripts/smoke-tenant/.local/")


if __name__ == "__main__":
    sys.exit(main())
