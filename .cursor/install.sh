#!/usr/bin/env bash
# Idempotent repository bootstrap for Lantern.
#
# The repo is polyglot: a .NET 10 solution (bridge protocol model + MCP helper host)
# and a Kotlin/JVM Gradle project (:core contract tests). The default Cloud Agent
# image already ships a JDK and Node, so this script only adds the missing .NET SDK
# and warms both toolchains' caches. It is safe to re-run.
set -euo pipefail

# Keep in sync with <TargetFramework> in Directory.Build.props.
dotnet_major="10"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

export DOTNET_CLI_TELEMETRY_OPTOUT=1
export DOTNET_NOLOGO=1
export DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

echo "==> Ensuring .NET ${dotnet_major} SDK is present"
if command -v dotnet >/dev/null 2>&1 && dotnet --list-sdks | grep -q "^${dotnet_major}\."; then
  echo "    .NET ${dotnet_major} SDK already installed: $(dotnet --list-sdks | grep "^${dotnet_major}\." | tail -1)"
else
  echo "    installing dotnet-sdk-${dotnet_major}.0 via apt"
  sudo apt-get update -qq
  sudo apt-get install -y -qq "dotnet-sdk-${dotnet_major}.0"
fi

echo "==> Restoring and building the .NET solution"
dotnet build Lantern.sln -c Release

echo "==> Warming the Kotlin/Gradle toolchain and building :core"
( cd src/mobile && ./gradlew :core:build --no-daemon )

echo "==> Bootstrap complete"
