namespace Lantern.Bridge.Identity;

/// <summary>
/// Filesystem-backend identity. Phase 0 offline tests never sign in. Calling
/// AcquireGraphTokenAsync is a programming error: the Graph store is not used.
/// </summary>
public sealed class HarnessIdentity : IBridgeIdentity
{
    public static HarnessIdentity Instance { get; } = new();

    public Task<string> AcquireGraphTokenAsync(CancellationToken cancellationToken = default) =>
        throw new InvalidOperationException(
            "Filesystem backend does not acquire Graph tokens. The Graph store is human-gated.");

    public Task<BridgeAccount?> GetCurrentAccountAsync(CancellationToken cancellationToken = default) =>
        Task.FromResult<BridgeAccount?>(new BridgeAccount(
            EntraObjectId: "00000000-0000-0000-0000-000000000000",
            Username: "harness@local",
            TenantId: "harness"));
}
