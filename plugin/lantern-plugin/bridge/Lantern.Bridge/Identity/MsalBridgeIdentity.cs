using Microsoft.Identity.Client;
using Microsoft.Identity.Client.Extensions.Msal;

namespace Lantern.Bridge.Identity;

/// <summary>
/// MSAL.NET public client. Silent first, then device-code by default or an
/// interactive browser when explicitly configured. The token cache uses
/// DPAPI, macOS Keychain, or Linux Keyring and never lands under
/// LANTERN_STATE_DIR.
/// </summary>
public sealed class MsalBridgeIdentity : IBridgeIdentity
{
    public static readonly string[] GraphScopes = ["Files.ReadWrite.AppFolder"];

    private readonly IPublicClientApplication _app;
    private readonly MsalCacheHelper _cacheHelper;
    private readonly bool _useDeviceCode;
    private readonly string? _tenantId;

    public MsalBridgeIdentity(string clientId, string? tenantId, string cacheDirectory, bool useDeviceCode)
    {
        if (string.IsNullOrWhiteSpace(clientId))
        {
            throw new ArgumentException("LANTERN_CLIENT_ID is required for the Graph backend.", nameof(clientId));
        }

        Directory.CreateDirectory(cacheDirectory);
        var authority = string.IsNullOrWhiteSpace(tenantId)
            ? "https://login.microsoftonline.com/common"
            : $"https://login.microsoftonline.com/{tenantId}";

        _app = PublicClientApplicationBuilder
            .Create(clientId)
            .WithAuthority(authority)
            .WithRedirectUri("http://localhost")
            .Build();
        _tenantId = string.IsNullOrWhiteSpace(tenantId) || string.Equals(tenantId, "common", StringComparison.OrdinalIgnoreCase)
            ? null
            : tenantId;

        var storage = new StorageCreationPropertiesBuilder("msal_cache.bin", cacheDirectory)
            .WithLinuxKeyring(
                schemaName: "dev.lantern.bridge.tokencache",
                collection: MsalCacheHelper.LinuxKeyRingDefaultCollection,
                secretLabel: "Lantern MSAL token cache",
                attribute1: new KeyValuePair<string, string>("Version", "1"),
                attribute2: new KeyValuePair<string, string>("Product", "Lantern"))
            .WithMacKeyChain(
                serviceName: "dev.lantern.bridge.tokencache",
                accountName: clientId)
            .Build();
        try
        {
            _cacheHelper = MsalCacheHelper.CreateAsync(storage).GetAwaiter().GetResult();
            _cacheHelper.VerifyPersistence();
            _cacheHelper.RegisterCache(_app.UserTokenCache);
        }
        catch (MsalCachePersistenceException error)
        {
            throw new InvalidOperationException(
                "An OS-protected MSAL cache is unavailable. Configure DPAPI, macOS Keychain, "
                + "or Linux Keyring before using the Graph backend.",
                error);
        }

        _useDeviceCode = useDeviceCode;
    }

    public async Task<string> AcquireGraphTokenAsync(CancellationToken cancellationToken = default)
    {
        var account = await ResolveAccountAsync().ConfigureAwait(false);
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
        var account = await ResolveAccountAsync().ConfigureAwait(false);
        if (account is null)
        {
            return null;
        }

        return new BridgeAccount(
            EntraObjectId: account.HomeAccountId.ObjectId ?? account.HomeAccountId.Identifier,
            Username: account.Username,
            TenantId: account.HomeAccountId.TenantId ?? "");
    }

    private async Task<IAccount?> ResolveAccountAsync()
    {
        var accounts = (await _app.GetAccountsAsync().ConfigureAwait(false))
            .Where(account => _tenantId is null
                || string.Equals(account.HomeAccountId.TenantId, _tenantId, StringComparison.OrdinalIgnoreCase))
            .ToList();
        return accounts.Count switch
        {
            0 => null,
            1 => accounts[0],
            _ => throw new InvalidOperationException(
                "Multiple cached Microsoft 365 accounts match the configured tenant. "
                + "Clear the Lantern MSAL cache and sign in with one account."),
        };
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
}
