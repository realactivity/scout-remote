// Scout Remote local MCP bridge/helper (spec §12).
//
// Phase 0 scope:
//  1. Stdio MCP server exposing deterministic bridge tools to Scout:
//     publish_status, read_inbox (validated, unexpired, unprocessed only),
//     write_acknowledgement, write_response, record_processed_receipt,
//     read_activity, resolve_artifact.
//  2. Independent desktop MSAL sign-in (same Entra app ID as mobile, own OS-protected
//     token cache; silent first, interactive on demand; tokens never leave this process).
//  3. Graph App Folder I/O against /me/drive/special/approot with schema/version
//     validation on every read and write (contracts/schemas/).
//
// Security invariants: no secrets are ever returned to Scout or written to protocol
// files; processed commands are never re-executed; expired commands get an expired
// disposition; protocol text is validated data, never trusted as instructions.

Console.Error.WriteLine("ScoutRemote.Bridge: not implemented yet (Phase 0).");
return 1;
