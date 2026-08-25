# docs/

Reference architecture, decision records, and brand system for Lantern.

The project is **paused** for community feedback. Start with
[`community.md`](community.md) and [`status.md`](status.md).

## Public status

| File | Purpose |
|---|---|
| [`community.md`](community.md) | Why work is paused, the mailbox-vs-wait question, and what feedback is useful. |
| [`status.md`](status.md) | Honest done vs not-done. Points at the evidence maps below. |

## Specification

| File | Purpose |
|---|---|
| [`design-spec-v2.0.md`](design-spec-v2.0.md) | **Authoritative design specification.** Lantern brand, Kotlin mobile framework, merged latency addendum, explicit compatibility policy (§15.2), and command-authenticity threat model (§11.2). |
| [`design-spec-v1.2.md`](design-spec-v1.2.md) | Historical v1.2 text, retained for provenance. The source PDF is not distributed in this repository. Superseded by v2.0. |

## Decision records

Merged decisions live in the spec; these records keep the full reasoning and,
where noted, the forward plan.

| File | Purpose |
|---|---|
| [`scout-openclaw-substrate.md`](scout-openclaw-substrate.md) | Decision and adoption roadmap: Scout runs on the OpenClaw substrate, but Lantern builds only on the exposed skills + MCP surfaces, never the raw gateway/nodes/channels. Pre-agrees Stages A–F for adopting each surface as Microsoft documents it (native plugin install, event-driven bridge, push, gateway API, mobile nodes, remote approvals), with per-stage triggers, preconditions, and threat-model deltas. |
| [`mobile-framework-kotlin.md`](mobile-framework-kotlin.md) | Merged into spec v2.0 (§16). The reasoning behind Kotlin + Jetpack Compose over .NET MAUI. |
| [`architecture-improvements.md`](architecture-improvements.md) | Merged into spec v2.0 (§16.2, §17.1, §19). The reasoning behind adaptive foreground polling, content-free Mode B push, and week-one App Folder validation. |

## Brand

| File | Purpose |
|---|---|
| [`lantern-brand-sheet.svg`](lantern-brand-sheet.svg) | Authoritative brand sheet (colors, status, type, iconography). Too tall for a README hero. |
| [`brand/`](brand/) | Compact extracts: icon, mark, hero, lockup, palette, status panes. |
| [`brand-guidelines.md`](brand-guidelines.md) | Human brand and UX spec: color system, the "pane is the status" mapping, typography, Compose checklist. |
| [`brand-tokens.json`](brand-tokens.json) | Machine-readable tokens. The Android Compose theme consumes this file. |

## Evidence

| File | Purpose |
|---|---|
| [`phase0-report.md`](phase0-report.md) | Phase 0 spike: what automation proves vs what remains human-gated. |
| [`enterprise-readiness.md`](enterprise-readiness.md) | Automated controls and the tenant, device, packaging, reliability, and security gates required before production. |
| [`client-metrics.md`](client-metrics.md) | Content-free runtime metrics, privacy boundary, and performance budgets. |

## Conventions

The authoritative spec format is markdown, versioned in git; PDFs are only kept
when an external original exists (as with v1.2). New spec revisions supersede
in place: bump the version, state what changed and why in the revision note,
and keep prior versions in history. Contract-affecting changes must be
reflected in [`../contracts/`](../contracts/) in the same change.
