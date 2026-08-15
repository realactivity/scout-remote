// Lantern local MCP bridge/helper (spec §12).
//
// Phase 0 scope:
//  1. Stdio MCP server exposing deterministic bridge tools to Scout:
//     publish_status, read_inbox (validated, unexpired, unprocessed only),
//     write_acknowledgement, write_response, record_processed_receipt,
//     read_activity, resolve_artifact.
//  2. Independent desktop MSAL sign-in (same Entra app ID as mobile, own OS-protected
//     token cache; silent first, interactive on demand; tokens never leave this process).
//  3. Graph App Folder I/O against /me/drive/special/approot with schema/version
//     validation on every read and write (contracts/schemas/).
//
// State directory contract: mutable bridge state (delta cursors, receipt mirror,
// diagnostics) lives under LANTERN_STATE_DIR. When launched by a conformant
// Agent Plugins client this expands to ${PLUGIN_DATA}/state (persists across plugin
// updates, spec §9.1); when the variable is unset or contains a literal unexpanded
// placeholder, fall back to the platform per-user app-data directory. MSAL token
// caches never go there. OS-protected storage only (design spec §10).
//
// Security invariants: no secrets are ever returned to Scout or written to protocol
// files; processed commands are never re-executed; expired commands get an expired
// disposition; protocol text is validated data, never trusted as instructions.
//
// Harness: --once processes the inbox against LANTERN_BACKEND=filesystem|graph
// and exits. Filesystem backend is the CI path (LANTERN_APPROOT).

using Lantern.Bridge.Identity;
using Lantern.Bridge.Protocol;
using Lantern.Bridge.Services;
using Lantern.Bridge.Storage;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using ModelContextProtocol.Server;

var once = args.Any(a => string.Equals(a, "--once", StringComparison.OrdinalIgnoreCase));
var host = BuildHost(args);

if (once)
{
    var bridge = host.Services.GetRequiredService<BridgeService>();
    var result = await bridge.ProcessOnceAsync().ConfigureAwait(false);
    Console.Error.WriteLine(
        $"Lantern.Bridge: processed {result.Executed} command(s), refused {result.Refused}.");
    return 0;
}

await host.RunAsync().ConfigureAwait(false);
return 0;

static IHost BuildHost(string[] args)
{
    var builder = Host.CreateApplicationBuilder(args);
    builder.Logging.ClearProviders();
    builder.Logging.AddConsole(options =>
    {
        options.LogToStandardErrorThreshold = LogLevel.Trace;
    });

    var backend = Environment.GetEnvironmentVariable("LANTERN_BACKEND") ?? InferBackend();
    var agentId = Environment.GetEnvironmentVariable("LANTERN_AGENT_ID") ?? "primary";
    var stateDir = BridgeState.ResolveDirectory(Environment.GetEnvironmentVariable("LANTERN_STATE_DIR"));
    var contractsDir = Environment.GetEnvironmentVariable("LANTERN_CONTRACTS_DIR");

    builder.Services.AddSingleton(_ => new SchemaValidator(contractsDir));
    builder.Services.AddSingleton(_ => new BridgeState(stateDir));
    builder.Services.AddSingleton<IBridgeIdentity>(_ => CreateIdentity(backend));
    builder.Services.AddSingleton<IAppFolderStore>(sp => CreateStore(backend, sp.GetRequiredService<IBridgeIdentity>()));
    builder.Services.AddSingleton(sp => new BridgeService(
        sp.GetRequiredService<IAppFolderStore>(),
        sp.GetRequiredService<SchemaValidator>(),
        sp.GetRequiredService<BridgeState>(),
        agentId));

    builder.Services
        .AddMcpServer()
        .WithStdioServerTransport()
        .WithTools<Lantern.Bridge.Tools.LanternBridgeTools>();

    return builder.Build();
}

static string InferBackend()
{
    var approot = Environment.GetEnvironmentVariable("LANTERN_APPROOT");
    return string.IsNullOrWhiteSpace(approot) ? "graph" : "filesystem";
}

static IBridgeIdentity CreateIdentity(string backend)
{
    if (string.Equals(backend, "filesystem", StringComparison.OrdinalIgnoreCase))
    {
        return HarnessIdentity.Instance;
    }

    var clientId = Environment.GetEnvironmentVariable("LANTERN_CLIENT_ID") ?? "";
    var tenantId = Environment.GetEnvironmentVariable("LANTERN_TENANT_ID");
    var useDeviceCode = !string.Equals(
        Environment.GetEnvironmentVariable("LANTERN_USE_DEVICE_CODE"),
        "0",
        StringComparison.Ordinal);
    return new MsalBridgeIdentity(clientId, tenantId, MsalBridgeIdentity.DefaultCacheDirectory(), useDeviceCode);
}

static IAppFolderStore CreateStore(string backend, IBridgeIdentity identity)
{
    if (string.Equals(backend, "filesystem", StringComparison.OrdinalIgnoreCase))
    {
        var approot = Environment.GetEnvironmentVariable("LANTERN_APPROOT");
        if (string.IsNullOrWhiteSpace(approot))
        {
            throw new InvalidOperationException("LANTERN_APPROOT is required when LANTERN_BACKEND=filesystem.");
        }

        return new FilesystemAppFolderStore(approot);
    }

    return new GraphAppFolderStore(identity);
}
