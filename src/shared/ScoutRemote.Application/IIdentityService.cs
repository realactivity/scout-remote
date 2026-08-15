namespace ScoutRemote.Application;

/// <summary>
/// MSAL sign-in and token acquisition (spec §10). Public client, PKCE, delegated scopes only.
/// Each client (Android, later iOS, desktop bridge) authenticates independently under the same
/// Entra app registration; tokens are never transferred between devices.
/// </summary>
public interface IIdentityService
{
    /// <summary>Least-privilege MVP scopes; broader scopes only for explicit features (spec §10.2).</summary>
    static readonly string[] MvpScopes = ["Files.ReadWrite.AppFolder"];

    Task<SignedInAccount?> GetCurrentAccountAsync(CancellationToken cancellationToken);

    /// <summary>Silent first; interactive only when tenant policy or token state requires it.</summary>
    Task<SignedInAccount> SignInAsync(CancellationToken cancellationToken);

    Task<string> AcquireGraphTokenAsync(CancellationToken cancellationToken);

    Task SignOutAsync(CancellationToken cancellationToken);
}

public sealed record SignedInAccount
{
    public required string EntraObjectId { get; init; }
    public required string Username { get; init; }
    public required string TenantId { get; init; }
}
