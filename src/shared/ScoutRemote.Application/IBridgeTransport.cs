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
}
