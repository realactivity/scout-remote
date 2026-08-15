# Architecture improvements

Latency and validation addendum.

**Status** Accepted, 15 August 2026. **Merged into
[design spec v2.0](design-spec-v2.0.md)** (§16.2 in-flight watch, §17.1 Mode B
constraints, §19 week-one scope validation); this file is retained as the
decision record and reasoning. Originally written as an addendum to
[design spec v1.2](design-spec-v1.2.md). Where this record and the spec differ
in emphasis, the spec's security boundaries always win. Nothing here weakens
the product boundary, the desktop approval model, or the no-token-sharing rule.

## Context

The v1.2 architecture is an asynchronous mailbox over the OneDrive App Folder.
End-to-end command latency is dominated by polling cadence on both ends, not by
any per-operation cost.

```
(time until bridge next polls, 0 to heartbeat interval, 15-120 min)
+ execution time
+ (time until mobile next refreshes, 0 to sync interval)
```

That is acceptable, and honest given the explicit-freshness UX rules in spec §9
and §15.1, for the primary "what is Scout doing?" use case. It is *not*
acceptable UX for the secondary interactive use case (send a bounded
instruction, wait for the answer) once that becomes a daily-driver feature.
This addendum adopts three improvements, in order of increasing cost.

## 1. Adaptive foreground polling

Adopt now (Phase 1).

**Decision.** While the app is in the foreground with a command in flight, the
sync service polls that command's response directly instead of waiting for the
normal refresh cycle.

- Poll `responses/{commandId}.json` by stored driveItem ID (one cheap Graph GET,
  no folder listing) on a short interval. Start at about 15-30 seconds.
- Back off exponentially toward the normal foreground cadence. Stop on terminal
  status (`completed`, `failed`, `expired`, `waiting_for_desktop_approval`,
  `canceled`) or when the app is backgrounded.
- **Foreground only.** Background cadence stays at OS-permitted intervals
  (spec §16.2). This must not fight Android Doze/standby or invite Graph
  throttling. Respect `Retry-After` on 429 and fold it into the backoff.
- Complementary bridge-side behavior (already required by spec §5.1, restated
  here as a latency feature). Write the `acknowledged` disposition immediately
  on pickup so the fast poll has something to show early.

**Where it lands.** `BridgeSyncService` gains an in-flight watch mode. No
protocol change, no new scopes, no infrastructure.

## 2. Push notifications (Mode B)

Adopt when interactive use grows.

**Decision.** Deployment Mode B (spec §17) remains out of the MVP, but is the
planned next step once bounded commands become routine rather than occasional.
The trigger is real usage, not the calendar. Pre-design now so it slots in
without a protocol change.

- Shape. A minimal Azure Function/API, a Microsoft Graph change notification
  subscription on the App Folder, and FCM to the Android device (APNs added
  with the Phase 4 iOS client).
- **Content-free push.** The notification payload carries only "something
  changed, sync". Never command text, response text, artifact names, or
  identifiers beyond what FCM routing needs. On receipt the app runs its
  normal validated sync. This keeps protocol data out of a third delivery
  channel and keeps the push service out of the data-trust boundary.
- The service holds no Graph tokens for the user beyond what the subscription
  model requires, stores no protocol content, and its compromise must degrade
  *latency only*, never confidentiality or the approval model.
- Net effect when adopted. Phone-side latency collapses to seconds, and
  battery/throttling load *drops* versus polling harder.

**Interaction with §1.** Adaptive foreground polling remains even after Mode B
ships. Push is best-effort delivery, and the foreground watch is the fallback
that keeps the UX honest when a push is dropped.

## 3. Early validation of the App Folder permission assumption

Phase 0, week one.

**Decision.** `Files.ReadWrite.AppFolder` support in the target tenant is the
highest-risk assumption in the design (flagged with an asterisk in spec §1 and
§10.2). Retire it first, before feature code.

- In the first week of Phase 0, from both a desktop and an Android MSAL public
  client under the real app registration in the real target tenant, acquire the
  scope, resolve `/me/drive/special/approot`, and complete a cross-device
  write/read round trip.
- Confirm admin-consent behavior and any tenant policy caveats for the scope
  while doing so.
- **If the scope is unusable in the target tenant**, the fallback is
  `Files.ReadWrite`, a materially weaker least-privilege story. That is an
  explicit, recorded decision (update spec §10.2's table and the threat model
  in §11), plus a defined protocol root path outside `approot`. It must not
  happen silently inside a transport implementation.
- Exit criterion added to Phase 0 (spec §19). The round trip above is
  demonstrated, or the fallback decision is recorded, before any Phase 1 work
  starts.

## What is deliberately not changed

No move toward a standing backend, relay, or direct device-to-desktop
connection for latency reasons. The two best properties of this architecture
are that there is no inbound path to the desktop and (in Mode A) no server to
defend. Real-time transports would spend both to fix a problem that §1 and §2
address more cheaply. The §20 architecture invariant holds. If Microsoft ships
a first-party Scout API or event channel, it arrives as a new transport adapter
behind the same domain interfaces.
