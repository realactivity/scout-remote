#!/usr/bin/env bash
# Idempotent repository bootstrap for Scout Remote.
#
# The repo is polyglot: a .NET 8 solution (bridge protocol model + MCP helper host)
# and a Kotlin/JVM Gradle project (:core contract tests). The default Cloud Agent
# image already ships a JDK and Node, so this script only adds the missing .NET SDK
# and warms both toolchains' caches. It is safe to re-run.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

export DOTNET_CLI_TELEMETRY_OPTOUT=1
export DOTNET_NOLOGO=1
export DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

echo "==> Ensuring .NET 8 SDK is present"
if ! command -v dotnet >/dev/null 2>&1; then
  echo "    dotnet not found; installing dotnet-sdk-8.0 via apt"
  sudo apt-get update -qq
  sudo apt-get install -y -qq dotnet-sdk-8.0
else
  echo "    dotnet already installed: $(dotnet --version)"
fi

echo "==> Restoring and building the .NET solution"
dotnet build ScoutRemote.sln -c Release

echo "==> Warming the Kotlin/Gradle toolchain and building :core"
( cd src/mobile && ./gradlew :core:build --no-daemon )

echo "==> Bootstrap complete"
