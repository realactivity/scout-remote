using Microsoft.Identity.Client;

namespace Lantern.Bridge.Identity;

/// <summary>
/// MSAL.NET public client. Silent first, then device-code or interactive when
/// LANTERN_USE_DEVICE_CODE is set. The token cache is a user-only file next to
/// the platform app-data directory, never under LANTERN_STATE_DIR.
/// </summary>
public sealed class MsalBridgeIdentity : IBridgeIdentity
{
    public static readonly string[] GraphScopes = ["Files.ReadWrite.AppFolder"];

    private readonly IPublicClientApplication _app;
    private readonly bool _useDeviceCode;

    public MsalBridgeIdentity(string clientId, string? tenantId, string cacheDirectory, bool useDeviceCode)
    {
        if (string.IsNullOrWhiteSpace(clientId))
        {
            throw new ArgumentException("LANTERN_CLIENT_ID is required for the Graph backend.", nameof(clientId));
        }

        Directory.CreateDirectory(cacheDirectory);
        var cachePath = Path.Combine(cacheDirectory, "msal_cache.bin");
        var authority = string.IsNullOrWhiteSpace(tenantId)
            ? "https://login.microsoftonline.com/common"
            : $"https://login.microsoftonline.com/{tenantId}";

        _app = PublicClientApplicationBuilder
            .Create(clientId)
            .WithAuthority(authority)
            .WithRedirectUri("http://localhost")
            .Build();

        _app.UserTokenCache.SetBeforeAccess(args =>
        {
            if (File.Exists(cachePath))
            {
                args.TokenCache.DeserializeMsalV3(File.ReadAllBytes(cachePath));
            }
        });
        _app.UserTokenCache.SetAfterAccess(args =>
        {
            if (!args.HasStateChanged)
            {
                return;
            }

            var bytes = args.TokenCache.SerializeMsalV3();
            var tmp = cachePath + ".tmp";
            File.WriteAllBytes(tmp, bytes);
            File.Move(tmp, cachePath, overwrite: true);
            TryRestrictPermissions(cachePath);
        });

        _useDeviceCode = useDeviceCode;
    }

    public async Task<string> AcquireGraphTokenAsync(CancellationToken cancellationToken = default)
    {
        var accounts = await _app.GetAccountsAsync().ConfigureAwait(false);
        var account = accounts.FirstOrDefault();
        try
        {
            if (account is not null)
            {
                var silent = await _app.AcquireTokenSilent(GraphScopes, account)
                    .ExecuteAsync(cancellationToken)
                    .ConfigureAwait(false);
                return silent.AccessToken;
            }
        }
        catch (MsalUiRequiredException)
        {
            // Fall through to interactive / device code.
        }

        if (_useDeviceCode)
        {
            var device = await _app.AcquireTokenWithDeviceCode(GraphScopes, result =>
            {
                Console.Error.WriteLine(result.Message);
                return Task.CompletedTask;
            }).ExecuteAsync(cancellationToken).ConfigureAwait(false);
            return device.AccessToken;
        }

        var interactive = await _app.AcquireTokenInteractive(GraphScopes)
            .ExecuteAsync(cancellationToken)
            .ConfigureAwait(false);
        return interactive.AccessToken;
    }

    public async Task<BridgeAccount?> GetCurrentAccountAsync(CancellationToken cancellationToken = default)
    {
        var accounts = await _app.GetAccountsAsync().ConfigureAwait(false);
        var account = accounts.FirstOrDefault();
        if (account is null)
        {
            return null;
        }

        return new BridgeAccount(
            EntraObjectId: account.HomeAccountId.ObjectId ?? account.HomeAccountId.Identifier,
            Username: account.Username,
            TenantId: account.HomeAccountId.TenantId ?? "");
    }

    internal static string DefaultCacheDirectory()
    {
        var root = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (string.IsNullOrWhiteSpace(root))
        {
            root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".local", "share");
        }

        return Path.Combine(root, "lantern", "msal");
    }

    private static void TryRestrictPermissions(string path)
    {
        try
        {
            if (OperatingSystem.IsWindows())
            {
                return;
            }

            File.SetUnixFileMode(path, UnixFileMode.UserRead | UnixFileMode.UserWrite);
        }
        catch (Exception)
        {
            // Best effort. The cache still lives outside the protocol tree.
        }
    }
}
