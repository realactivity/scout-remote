# Security policy

Lantern is security-sensitive because it bridges a mobile client, Microsoft
Graph, and a desktop automation surface. Please do not report vulnerabilities
in a public issue.

## Reporting

Use GitHub private vulnerability reporting from the repository Security tab.
If that feature is unavailable, contact the repository owner through the
organization's established private security channel.

Include:

- the affected commit and component
- reproduction steps or a minimal proof of concept
- expected and observed behavior
- whether credentials, protocol files, or desktop actions are exposed
- any suggested mitigation

Do not include access tokens, refresh tokens, device codes, tenant data, or
other secrets in the report. Use synthetic values and redact logs.

## Supported versions

The `main` branch is the only supported development line. There is no supported
production release yet. The project is paused after a Phase 0 spike
(`docs/community.md`). Phase 0 proves the offline protocol path; real-tenant
authentication, Android Graph wiring, Scout policy, signing, and device
acceptance remain release gates in `docs/enterprise-readiness.md`.

## Response priorities

Credential disclosure, remote approval bypass, command replay, command
execution without validation, and cross-tenant data exposure receive the
highest priority. A fix is not complete until regression coverage proves the
affected trust boundary.
