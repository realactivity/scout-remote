# docs/

Reference architecture for Lantern.

| File | Purpose |
|---|---|
| [`Scout-Remote-Design-Spec-v1.2.pdf`](Scout-Remote-Design-Spec-v1.2.pdf) | Original design spec (authoritative). |
| [`design-spec-v1.2.md`](design-spec-v1.2.md) | Markdown conversion for diff-friendly review and linking from code. |
| [`architecture-improvements.md`](architecture-improvements.md) | Accepted addendum. Adaptive foreground polling (Phase 1), Mode B push when interactive use grows, and early Phase 0 validation of the App Folder permission assumption. |
| [`mobile-framework-kotlin.md`](mobile-framework-kotlin.md) | Accepted decision. The mobile client is Kotlin and Jetpack Compose (supersedes the spec §16 MAUI recommendation). The desktop bridge stays on .NET. `contracts/` is the language-neutral boundary. |
| [`lantern-brand-sheet.svg`](lantern-brand-sheet.svg) | Authoritative brand sheet for the **lantern** mobile client (colors, status system, typography, iconography). |
| [`brand-guidelines.md`](brand-guidelines.md) | Human brand and UX spec: color system, the "pane is the status" mapping to the protocol state model, typography, and a Compose adherence checklist. |
| [`brand-tokens.json`](brand-tokens.json) | Machine-readable brand tokens extracted from the brand sheet; the source the Android Compose theme should consume. |

New spec revisions should be committed as a new version pair (PDF and markdown),
keeping prior versions in history. Contract-affecting changes must be reflected
in [`../contracts/`](../contracts/) in the same change.
