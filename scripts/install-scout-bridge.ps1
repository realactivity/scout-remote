#Requires -Version 5.1
<#
.SYNOPSIS
  Install the Lantern skill and register lantern-bridge as a local stdio MCP server.

.DESCRIPTION
  Writes the Copilot CLI / Scout shared MCP file and the Scout-specific
  m-mcp-servers.json. Copies the skill folder. Does not click Scout's
  Extensions UI and does not grant MCP permissions. Quit Scout before running.

  Client ID and tenant ID come from parameters or LANTERN_CLIENT_ID /
  LANTERN_TENANT_ID. They are written only into the local MCP env block.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = "",
    [string] $ClientId = $env:LANTERN_CLIENT_ID,
    [string] $TenantId = $env:LANTERN_TENANT_ID,
    [switch] $Publish,
    [switch] $ForceScoutRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    param([string] $Hint)
    if ($Hint) {
        return (Resolve-Path -LiteralPath $Hint).Path
    }
    if ($PSScriptRoot) {
        $fromScript = Split-Path -Parent $PSScriptRoot
        if (Test-Path -LiteralPath (Join-Path $fromScript "plugin\lantern-plugin\mcp.json")) {
            return $fromScript
        }
    }
    $cwd = (Get-Location).Path
    if (Test-Path -LiteralPath (Join-Path $cwd "plugin\lantern-plugin\mcp.json")) {
        return $cwd
    }
    throw "Pass -RepoRoot to the scout-remote checkout."
}

function Assert-ScoutStopped {
    $names = @("Scout", "Microsoft Scout", "Clawpilot")
    $running = Get-Process | Where-Object { $names -contains $_.ProcessName }
    if ($running -and -not $ForceScoutRunning) {
        $list = ($running | ForEach-Object { "$($_.ProcessName) pid=$($_.Id)" }) -join "; "
        throw "Quit Scout first, then re-run. Still running: $list"
    }
}

function Merge-LanternServer {
    param(
        [string] $Path,
        [hashtable] $Server
    )
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }

    $root = [ordered]@{ mcpServers = [ordered]@{} }
    if (Test-Path -LiteralPath $Path) {
        $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
        if ($raw.Trim().Length -gt 0) {
            $parsed = $raw | ConvertFrom-Json
            if ($parsed.mcpServers) {
                foreach ($prop in $parsed.mcpServers.PSObject.Properties) {
                    if ($prop.Name -eq "lantern-bridge") { continue }
                    $root.mcpServers[$prop.Name] = $prop.Value
                }
            }
            elseif ($parsed.servers) {
                $root = [ordered]@{ servers = [ordered]@{} }
                foreach ($prop in $parsed.servers.PSObject.Properties) {
                    if ($prop.Name -eq "lantern-bridge") { continue }
                    $root.servers[$prop.Name] = $prop.Value
                }
                $root.servers["lantern-bridge"] = $Server
                $json = $root | ConvertTo-Json -Depth 8
                Set-Content -LiteralPath $Path -Value $json -Encoding UTF8
                return "merged-servers"
            }
        }
    }

    $root.mcpServers["lantern-bridge"] = $Server
    $json = $root | ConvertTo-Json -Depth 8
    Set-Content -LiteralPath $Path -Value $json -Encoding UTF8
    return "merged-mcpServers"
}

$RepoRoot = Resolve-RepoRoot -Hint $RepoRoot
if ([string]::IsNullOrWhiteSpace($ClientId) -or [string]::IsNullOrWhiteSpace($TenantId)) {
    throw "Set -ClientId and -TenantId, or LANTERN_CLIENT_ID and LANTERN_TENANT_ID."
}

Assert-ScoutStopped

$dotnet = Join-Path ${env:ProgramFiles} "dotnet\dotnet.exe"
if (-not (Test-Path -LiteralPath $dotnet)) {
    $dotnet = "dotnet"
}

$publishDir = Join-Path $RepoRoot "plugin\lantern-plugin\bridge\publish"
$dll = Join-Path $publishDir "Lantern.Bridge.dll"
if ($Publish -or -not (Test-Path -LiteralPath $dll)) {
    Write-Host "Publishing Lantern.Bridge..."
    & $dotnet restore (Join-Path $RepoRoot "plugin\lantern-plugin\bridge\Lantern.Bridge") --locked-mode
    if ($LASTEXITCODE -ne 0) { throw "dotnet restore failed with $LASTEXITCODE" }
    & $dotnet publish (Join-Path $RepoRoot "plugin\lantern-plugin\bridge\Lantern.Bridge") -c Release --no-restore -o $publishDir
    if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed with $LASTEXITCODE" }
}
if (-not (Test-Path -LiteralPath $dll)) {
    throw "Missing $dll. Re-run with -Publish."
}

$skillSrc = Join-Path $RepoRoot "plugin\lantern-plugin\skills\lantern-bridge"
$skillDst = Join-Path $env:USERPROFILE ".copilot\skills\lantern-bridge"
New-Item -ItemType Directory -Path (Split-Path -Parent $skillDst) -Force | Out-Null
Copy-Item -LiteralPath $skillSrc -Destination $skillDst -Recurse -Force
Write-Host "SKILL_COPIED=$skillDst"

$stateDir = Join-Path $env:USERPROFILE ".copilot\lantern-bridge-state"
New-Item -ItemType Directory -Path $stateDir -Force | Out-Null

$server = [ordered]@{
    type    = "local"
    command = $dotnet
    args    = @($dll)
    tools   = @("*")
    env     = [ordered]@{
        LANTERN_BACKEND          = "graph"
        LANTERN_CLIENT_ID        = $ClientId
        LANTERN_TENANT_ID        = $TenantId
        LANTERN_USE_DEVICE_CODE  = "0"
        LANTERN_AGENT_ID         = "primary"
        LANTERN_STATE_DIR        = $stateDir
    }
}

$targets = @(
    (Join-Path $env:USERPROFILE ".copilot\mcp-config.json"),
    (Join-Path $env:USERPROFILE ".copilot\m-mcp-servers.json")
)
$scoutProfile = Join-Path $env:APPDATA "Microsoft Scout"
if (Test-Path -LiteralPath $scoutProfile) {
    Get-ChildItem -LiteralPath $scoutProfile -Recurse -File -Filter "*mcp*.json" -ErrorAction SilentlyContinue |
        ForEach-Object { $targets += $_.FullName }
}

$written = @()
foreach ($path in ($targets | Select-Object -Unique)) {
    $how = Merge-LanternServer -Path $path -Server $server
    Write-Host "MCP_WRITTEN=$path ($how)"
    $written += $path
}

Write-Host ""
Write-Host "DLL=$dll"
Write-Host "NEXT=start Scout, open a new chat, then ⋯ → Settings → Manage Permissions → MCP SERVERS and enable lantern-bridge"
Write-Host "If Extensions → MCP Servers still omits lantern-bridge, use + Add Server (Command) with the same command/args/env."
Write-Host "This script cannot flip Scout's in-app enable toggle."
