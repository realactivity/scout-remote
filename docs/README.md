# docs/

Reference architecture, decision records, and brand system for Lantern.

## Specification

| File | Purpose |
|---|---|
| [`design-spec-v2.0.md`](design-spec-v2.0.md) | **Authoritative design specification.** Lantern brand, Kotlin mobile framework, merged latency addendum, explicit compatibility policy (§15.2), and command-authenticity threat model (§11.2). |
| [`design-spec-v1.2.md`](design-spec-v1.2.md) | Historical. Markdown conversion of the original v1.2 spec, retained for provenance. Superseded by v2.0. |
| [`Scout-Remote-Design-Spec-v1.2.pdf`](Scout-Remote-Design-Spec-v1.2.pdf) | Historical. The original v1.2 document, authored under the working name "Scout Remote". Superseded by v2.0. |

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
| [`lantern-brand-sheet.svg`](lantern-brand-sheet.svg) | Authoritative brand sheet for the **lantern** mobile client (colors, status system, typography, iconography). |
| [`brand-guidelines.md`](brand-guidelines.md) | Human brand and UX spec: color system, the "pane is the status" mapping to the protocol state model, typography, and a Compose adherence checklist. |
| [`brand-tokens.json`](brand-tokens.json) | Machine-readable brand tokens extracted from the brand sheet; the source the Android Compose theme consumes. |
| [`phase0-report.md`](phase0-report.md) | Phase 0 spike: what automation proves vs what remains human-gated. |

## Conventions

The authoritative spec format is markdown, versioned in git; PDFs are only kept
when an external original exists (as with v1.2). New spec revisions supersede
in place: bump the version, state what changed and why in the revision note,
and keep prior versions in history. Contract-affecting changes must be
reflected in [`../contracts/`](../contracts/) in the same change.
