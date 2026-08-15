# docs/

Reference architecture for Lantern.

| File | Purpose |
|---|---|
| [`design-spec-v2.0.md`](design-spec-v2.0.md) | **Authoritative design specification.** Lantern brand, Kotlin mobile framework, merged latency addendum, explicit compatibility policy (§15.2), and command-authenticity threat model (§11.2). |
| [`design-spec-v1.2.md`](design-spec-v1.2.md) | Historical. Markdown conversion of the original v1.2 spec, retained for provenance. Superseded by v2.0. |
| [`Scout-Remote-Design-Spec-v1.2.pdf`](Scout-Remote-Design-Spec-v1.2.pdf) | Historical. The original v1.2 document, authored under the working name "Scout Remote". Superseded by v2.0. |
| [`architecture-improvements.md`](architecture-improvements.md) | Decision record, merged into spec v2.0 (§16.2, §17.1, §19). Retained for the reasoning behind adaptive foreground polling, content-free Mode B push, and week-one App Folder validation. |
| [`mobile-framework-kotlin.md`](mobile-framework-kotlin.md) | Decision record, merged into spec v2.0 (§16). Retained for the reasoning behind Kotlin + Jetpack Compose over .NET MAUI. |
| [`scout-openclaw-substrate.md`](scout-openclaw-substrate.md) | Decision record: Scout runs on the OpenClaw substrate, but Lantern builds only on the exposed skills + MCP surfaces, never the raw gateway/nodes/channels. Includes concrete install paths and the week-one tenant policy check. |
| [`lantern-brand-sheet.svg`](lantern-brand-sheet.svg) | Authoritative brand sheet for the **lantern** mobile client (colors, status system, typography, iconography). |
| [`brand-guidelines.md`](brand-guidelines.md) | Human brand and UX spec: color system, the "pane is the status" mapping to the protocol state model, typography, and a Compose adherence checklist. |
| [`brand-tokens.json`](brand-tokens.json) | Machine-readable brand tokens extracted from the brand sheet; the source the Android Compose theme should consume. |

The authoritative spec format is markdown, versioned in git; PDFs are only kept
when an external original exists (as with v1.2). New spec revisions supersede
in place: bump the version, state what changed and why in the revision note,
and keep prior versions in history. Contract-affecting changes must be
reflected in [`../contracts/`](../contracts/) in the same change.
