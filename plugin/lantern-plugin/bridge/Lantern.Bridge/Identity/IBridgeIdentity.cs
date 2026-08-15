namespace Lantern.Bridge.Identity;

/// <summary>
/// Desktop MSAL public-client identity (spec §10.3). Silent first. Tokens never
/// leave this process and never land in LANTERN_STATE_DIR or protocol files.
/// </summary>
public interface IBridgeIdentity
{
    Task<string> AcquireGraphTokenAsync(CancellationToken cancellationToken = default);

    Task<BridgeAccount?> GetCurrentAccountAsync(CancellationToken cancellationToken = default);
}

public sealed record BridgeAccount(string EntraObjectId, string Username, string TenantId);
