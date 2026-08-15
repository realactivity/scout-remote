using ScoutRemote.Domain;

namespace ScoutRemote.Application;

/// <summary>
/// Port for the bridge data plane. The MVP implementation is the Graph App Folder adapter
/// (ScoutRemote.Transport.Graph); if Microsoft ships a first-party Scout API, it becomes a
/// new adapter behind this same interface — the architecture invariant of spec §20.
/// </summary>
public interface IBridgeTransport
{
    Task<ProtocolManifest?> ReadProtocolAsync(CancellationToken cancellationToken);

    Task<AgentStatus?> ReadStatusAsync(string agentId, CancellationToken cancellationToken);

    /// <summary>
    /// Writes a command to the inbox. Must be idempotent per CommandId: retries after a
    /// transient failure never produce a duplicate command (spec §15).
    /// </summary>
    Task EnqueueCommandAsync(RemoteCommand command, CancellationToken cancellationToken);

    Task<RemoteResponse?> ReadResponseAsync(string commandId, CancellationToken cancellationToken);

    /// <summary>
    /// Reads the append-only event stream for one bridge-owned conversation, ordered by
    /// EventId (ULIDs sort chronologically). Chats screen, spec §8.1/§9.
    /// </summary>
    Task<IReadOnlyList<ConversationEvent>> ReadConversationEventsAsync(
        string conversationId,
        CancellationToken cancellationToken);

    /// <summary>
    /// Reads activity events at or after <paramref name="sinceUtc"/> — the foreground
    /// "activity delta" of spec §16.2. Activity screen, spec §9.
    /// </summary>
    Task<IReadOnlyList<ActivityEvent>> ReadActivityAsync(
        DateTimeOffset sinceUtc,
        CancellationToken cancellationToken);

    /// <summary>
    /// Resolves an artifact reference to OneDrive item details for the Artifacts screen.
    /// Returns null when the item was deleted or is no longer accessible — the caller marks
    /// it unavailable rather than failing (spec §15).
    /// </summary>
    Task<ArtifactDetails?> ResolveArtifactAsync(string driveItemId, CancellationToken cancellationToken);
}
