# Contributing

Lantern is a security-sensitive, polyglot repository. Changes should preserve
the protocol boundary and keep claims tied to executable evidence.

## Development rules

- Sign every commit with the Developer Certificate of Origin:

  ```bash
  git commit -s
  ```

- Keep Android APIs in `src/mobile/app`. The Kotlin `core` module stays pure
  JVM code.
- Treat command text and every protocol text field as untrusted data.
- Never write credentials, device codes, auth headers, or token caches into
  protocol files, fixtures, diagnostics, or screenshots.
- Do not broaden Graph permissions without an explicit threat-model and
  documentation update.
- Contract changes must update the schema, golden examples, both language
  models, compatibility notes, and cross-language tests in one pull request.
- Keep `docs/design-spec-v1.2.*` as historical source material.

## Validation

Run the checks relevant to your change. Protocol and bridge changes should run
the full set.

```bash
dotnet restore Lantern.sln --locked-mode
dotnet build Lantern.sln -c Release --no-restore
dotnet run --project src/shared/Lantern.Domain.Tests/Lantern.Domain.Tests.csproj -c Release --no-build --no-restore -- -noColor -noLogo
dotnet run --project plugin/lantern-plugin/bridge/Lantern.Bridge.Tests/Lantern.Bridge.Tests.csproj -c Release --no-build --no-restore -- -noColor -noLogo
( cd src/mobile && ./gradlew :core:build --no-daemon )
( cd src/mobile && ./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon )
python3 -m pip install --requirement scripts/requirements-ci.txt
python3 contracts/validate_examples.py
scripts/smoke-e2e/run.sh
python3 scripts/smoke-mcp/run.py
```

Real-tenant checks are human-gated and must use
`scripts/smoke-tenant/README.md`. Never commit its `.local/` directory.

## Pull requests

Keep each pull request focused. Explain the trust boundary affected, list the
tests actually run, and distinguish automated proof from human-gated evidence.
Reviewers should reject changes that weaken strict inbox validation, expiry,
idempotency, terminal response immutability, least privilege, or secret
hygiene.
