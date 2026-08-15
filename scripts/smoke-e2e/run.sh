#!/usr/bin/env bash
# Cross-language Phase 0 smoke: Kotlin client + Lantern.Bridge --once.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
dotnet restore plugin/lantern-plugin/bridge/Lantern.Bridge --locked-mode
dotnet publish plugin/lantern-plugin/bridge/Lantern.Bridge -c Release --no-restore -o plugin/lantern-plugin/bridge/publish
export LANTERN_BRIDGE_DLL="$root/plugin/lantern-plugin/bridge/publish/Lantern.Bridge.dll"
export LANTERN_REQUIRE_E2E=1
( cd src/mobile && ./gradlew :core:test --tests dev.lantern.core.smoke.CrossLanguageSmokeTest )
