using ScoutRemote.Application;
using ScoutRemote.Domain;

namespace ScoutRemote.Transport.Graph;

/// <summary>
/// IBridgeTransport over the OneDrive App Folder (/me/drive/special/approot), spec §6 and §16.2.
/// Implementation notes for Phase 0:
///  - ETags/If-Match for mutable files: status, settings, and in-flight responses (a response
///    file is updated through acknowledged/running and becomes immutable only at a terminal
///    status, spec §15.1). Commands and conversation/activity events are immutable once written.
///  - Store driveItem IDs and delta cursors; never re-list the whole app folder per refresh.
///  - Validate protocol.json (schema + major version) before touching any other file.
///  - Enqueue uses create-if-absent semantics keyed on CommandId so retries cannot duplicate.
/// </summary>
public sealed class GraphBridgeTransport : IBridgeTransport
{
    public Task<ProtocolManifest?> ReadProtocolAsync(CancellationToken cancellationToken) =>
        throw new NotImplementedException("Phase 0: implement against Microsoft Graph App Folder.");

    public Task<AgentStatus?> ReadStatusAsync(string agentId, CancellationToken cancellationToken) =>
        throw new NotImplementedException("Phase 0: implement against Microsoft Graph App Folder.");

    public Task EnqueueCommandAsync(RemoteCommand command, CancellationToken cancellationToken) =>
        throw new NotImplementedException("Phase 0: implement against Microsoft Graph App Folder.");

    public Task<RemoteResponse?> ReadResponseAsync(string commandId, CancellationToken cancellationToken) =>
        throw new NotImplementedException("Phase 0: implement against Microsoft Graph App Folder.");

    public Task<IReadOnlyList<ConversationEvent>> ReadConversationEventsAsync(string conversationId, CancellationToken cancellationToken) =>
        throw new NotImplementedException("Phase 1: implement against Microsoft Graph App Folder.");

    public Task<IReadOnlyList<ActivityEvent>> ReadActivityAsync(DateTimeOffset sinceUtc, CancellationToken cancellationToken) =>
        throw new NotImplementedException("Phase 1: implement against Microsoft Graph App Folder.");

    public Task<ArtifactDetails?> ResolveArtifactAsync(string driveItemId, CancellationToken cancellationToken) =>
        throw new NotImplementedException("Phase 1: implement against Microsoft Graph App Folder.");
}
