# Community notes

Lantern is paused on purpose. Phase 0 proved an offline protocol path. The
next decision is architectural, and it should not be made in a private
branch.

This page is the ask. Prefer a GitHub issue using
[`.github/ISSUE_TEMPLATE/architecture-idea.yml`](../.github/ISSUE_TEMPLATE/architecture-idea.yml)
so the argument is durable. Security issues still go through
[`SECURITY.md`](../SECURITY.md), never a public issue.

Lantern is an independent companion. It is not a Microsoft product.

## The uncertainty

The current design hangs a light in a shared place: versioned JSON in the
user's OneDrive App Folder. The phone writes a command. The desktop bridge
reads it, Scout decides, a response file comes back. Sender and receiver are
never directly connected.

That will **pass commands**. The open question is whether it is **good
enough**.

A file mailbox is least-privilege and user-owned. It is also a compromise:

- latency is "whenever both sides poll," not a session
- presence is `lastSeenUtc`, not a live connection
- the vocabulary is whatever we put in `contracts/`
- Scout still has to run a local MCP helper and a copied skill; it does not
  install Agent Plugins 1.0 packages natively
- nothing here is proven on a real tenant or a real phone

The other instinct: **build the product around stable domain interfaces
now**, and treat Graph / App Folder as a transport adapter. Spec v2.0 already
says this. If Microsoft later ships a native Scout API, swap the adapter.
The risk is shipping a mailbox that feels like a hack and then being stuck
with its UX until the swap.

A third option is welcome. That is why this is public.

## Questions worth answering

These are the useful ones. "Looks cool" is not.

1. **Is an App Folder command mailbox a product, or only a prototype bus?**
   If you have shipped Graph + App Folder as a cross-device control plane,
   what broke in practice (throttling, ETag races, share semantics, tenant
   admin blocks)?
2. **What should "mobile companion" mean while Scout approvals stay on the
   desktop?** Glanceable status? Bounded commands? Artifact pickup? Something
   else? What must *never* leave the desktop?
3. **Should we wait for Microsoft APIs?** If you work on Scout, MCP, or
   Agent Plugins: what is actually documented, what is Frontier-only, and
   what would make a third-party companion unnecessary?
4. **Is Agent Plugins 1.0 the right desktop packaging even if Scout does
   not load the package yet?** The bundle in `plugin/lantern-plugin/` is
   schema-valid against 1.0.0. Install is still skill copy + MCP register.
   Is that a useful preview, or premature?
5. **What would you delete from Phase 0 before writing Phase 1?** Strict
   inbox, receipts, delta sync, content-free metrics — keep or cut?
6. **Is there a better mailbox** that still avoids a Lantern-operated
   backend (Mode A: personal, no server of ours)?

## Two default paths (neither chosen)

| Path | Bet | Cost if wrong |
|---|---|---|
| **A. Build the mailbox** | Domain interfaces stay; Graph is swappable; community can try a real round trip once Entra exists. | We spend Phase 1 on a bus that never feels like a product. |
| **B. Wait for first-party surfaces** | Do not invent a transport Microsoft will replace. | The pause can last a long time. Scout may not expose mobile-friendly APIs. |

Argue for A, B, or C. "C" needs a sketch, a trust boundary, and what it
refuses to do (especially remote approvals).

## What not to send

- Requests to start Phase 1 implementation while this pause holds
- Exploit write-ups or token-handling bugs in public issues — use
  [`SECURITY.md`](../SECURITY.md)
- Tenant IDs, tokens, `.local/` smoke-kit output, or screenshots of real
  protocol files with user content
- Gateway / nodes / channels work against the undocumented OpenClaw
  substrate ([`scout-openclaw-substrate.md`](scout-openclaw-substrate.md))

## How to contribute during the pause

Discussion and documentation clarifications are welcome. Protocol or code
changes should be small, evidence-tied, and should not expand the product
surface. See [`CONTRIBUTING.md`](../CONTRIBUTING.md).

If you only have time for one artifact: open an architecture issue that
picks a path and names the failure mode you have already seen.
