#!/usr/bin/env bash
# Idempotent repository bootstrap for Lantern.
#
# The repo is polyglot: a .NET 10 solution (bridge protocol model + MCP helper host)
# and a Kotlin/JVM Gradle project (:core contract tests). The default Cloud Agent
# image already ships a JDK and Node, so this script only adds the missing .NET SDK
# and warms both toolchains' caches. It is safe to re-run.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
dotnet_sdk="$(python3 -c 'import json; print(json.load(open("global.json"))["sdk"]["version"])')"

export DOTNET_CLI_TELEMETRY_OPTOUT=1
export DOTNET_NOLOGO=1
export DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

echo "==> Ensuring a global.json-compatible .NET SDK is present"
if command -v dotnet >/dev/null 2>&1 && dotnet --version >/dev/null 2>&1; then
  echo "    compatible .NET SDK already installed: $(dotnet --version)"
else
  echo "    installing .NET SDK ${dotnet_sdk}"
  installer="$(mktemp)"
  trap 'rm -f "$installer"' EXIT
  curl --fail --location --silent --show-error \
    https://dot.net/v1/dotnet-install.sh \
    --output "$installer"
  bash "$installer" --version "$dotnet_sdk" --install-dir "$HOME/.dotnet"
  sudo ln -sfn "$HOME/.dotnet/dotnet" /usr/local/bin/dotnet
  export PATH="$HOME/.dotnet:$PATH"
fi

echo "==> Restoring and building the .NET solution"
dotnet restore Lantern.sln --locked-mode
dotnet build Lantern.sln -c Release --no-restore

echo "==> Warming the Kotlin/Gradle toolchain and building :core"
( cd src/mobile && ./gradlew :core:build --no-daemon )

echo "==> Bootstrap complete"
