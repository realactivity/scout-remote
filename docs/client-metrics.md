# Client runtime metrics

Lantern runtime metrics support performance analysis, throttling tests, offline
recovery, and reliability soaks. They are separate from protocol usage files
and from the per-command bridge journal.

## Privacy boundary

Allowed values:

- operation enum
- result category
- duration in milliseconds
- retry count
- queue/cache entry count
- sync age

Forbidden values:

- command, conversation, drive item, account, device, or tenant identifiers
- App Folder paths or request URIs
- command, response, activity, or conversation text
- access tokens, refresh tokens, device codes, or authorization headers
- exception messages that may contain remote data

The core `LanternMetrics` interface accepts only the allowed typed values.
Android currently writes these content-free records to Logcat. Tests use an
in-memory collector. No runtime metric is sent to OneDrive or a cloud service.

## Metrics and audit are different

| Data | Purpose | Location |
|---|---|---|
| Runtime metrics | Aggregate performance and reliability | Local platform sink |
| Bridge journal | Per-command processing audit | `LANTERN_STATE_DIR/diagnostics/journal.jsonl` |
| Usage aggregate | User-visible Work IQ/model counters | `telemetry/usage/{yyyy-MM-dd}.json` |

Runtime metrics must never replace the bridge journal for audit, and the
journal must not be exported as aggregate telemetry.

## Performance budgets

Steady-state sync should satisfy:

- unchanged protocol/status reads return `NotModified` without downloading content
- immutable activity and conversation events are read from the protected cache
- response polling uses a cached drive item ID after discovery
- delta links replace repeated recursive folder listings
- retry loops are bounded and report only status category, duration, and count

CI tests assert conditional-read request counts, delta pagination/path handling,
retry metrics, cursor persistence, and payload separation through the
`SecureStorage` port. Device tests remain responsible for Keystore acceptance.

Application Insights or another remote APM sink remains out of scope for Mode
A. A remote sink requires a separate privacy review and becomes relevant only
with Mode B or managed fleet operations.
