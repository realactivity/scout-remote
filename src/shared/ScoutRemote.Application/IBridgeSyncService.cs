using ScoutRemote.Domain;

namespace ScoutRemote.Application;

/// <summary>
/// Sync orchestration (spec §16.2). Foreground: protocol → status → active conversation →
/// activity delta. Background: lightweight status/response checks at OS-permitted intervals.
/// Uses ETags for mutable files and stored delta cursors to avoid re-listing the app folder.
/// </summary>
public interface IBridgeSyncService
{
    Task<SyncResult> RefreshForegroundAsync(CancellationToken cancellationToken);

    Task<SyncResult> RefreshBackgroundAsync(CancellationToken cancellationToken);
}

public sealed record SyncResult
{
    public required DateTimeOffset SyncedUtc { get; init; }
    public AgentStatus? Status { get; init; }
    public IReadOnlyList<RemoteResponse> NewResponses { get; init; } = [];
    public bool ProtocolIncompatible { get; init; }
}
