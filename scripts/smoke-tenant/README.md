# Real-tenant smoke kit

Human-gated Phase 0 check against a real Microsoft 365 tenant. CI does not
run this. The offline harness already proves the protocol. This kit proves
the week-one tenant assumptions in spec §19.

## What it proves

| Check | Spec |
|---|---|
| `Files.ReadWrite.AppFolder` acquired | §10.2, §19 |
| `/me/drive/special/approot` resolved from a desktop persona and a mobile-sim persona | §19, §21.1 |
| Cross-device write/read of `protocol.json` and one command | §21.1 |
| Scout tenant-policy checklist printed for a human to confirm | `docs/scout-openclaw-substrate.md` |

Runtime is under 30 minutes if the Entra public-client app already exists.

## NEVER-COMMIT

Do not commit anything this kit writes locally:

| Path | Why |
|---|---|
| `scripts/smoke-tenant/.local/` | Device-code cache, Graph dumps, logs |
| `msal_cache*` | Token cache |
| `appsettings.local.json` | Local secrets |

`.local/` is gitignored. Treat every file in it as credential-adjacent.

## Environment (no files)

| Variable | Required | Meaning |
|---|---|---|
| `LANTERN_CLIENT_ID` | yes | Entra public-client application ID |
| `LANTERN_TENANT_ID` | yes | Tenant ID or `common` |
| `LANTERN_USE_DEVICE_CODE` | no | Set to `1` (default) for device-code sign-in |
| `LANTERN_RUN_BRIDGE` | no | Set to `1` to run `Lantern.Bridge --once` against Graph after the mobile-sim write |

## Run

```bash
python3 -m pip install --user -r scripts/smoke-tenant/requirements.txt
export LANTERN_CLIENT_ID="..."
export LANTERN_TENANT_ID="..."
python3 scripts/smoke-tenant/smoke.py
```

Graph calls retry once on HTTP 429 or 503 using `Retry-After`.

The script signs in once (device code), then uses that session as both the
desktop and mobile-sim persona. That matches Mode A: one user, two clients,
no token sharing between devices in production. Here a single human session
stands in for both ends.

If `LANTERN_RUN_BRIDGE=1`, also set `LANTERN_BACKEND=graph` and run from a
machine that can complete MSAL for the bridge process.

## Scout policy checklist (human)

Confirm in the tenant before calling Phase 0 done:

1. Heartbeat / automations are not disabled under
   `HKLM\SOFTWARE\Policies\Scout`.
2. Local stdio MCP servers are allowed.
3. Admin consent for `Files.ReadWrite.AppFolder` behaves as expected.

The script prints this list and records `HUMAN` for those rows. It cannot
see Intune policy from Graph App Folder access alone.
