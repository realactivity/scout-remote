using ScoutRemote.Domain;

namespace ScoutRemote.Application;

/// <summary>
/// Command lifecycle: draft → queued (durable) → observed response states.
/// Drafts survive offline (cached locally, retried with backoff); a command is a durable job —
/// the user can close the app immediately after sending (spec §5, §15).
/// </summary>
public interface ICommandService
{
    /// <summary>Creates and enqueues a command with a fresh ULID and the configured expiry window.</summary>
    Task<RemoteCommand> SendAsync(
        CommandType type,
        string conversationId,
        string? text,
        CommandConstraints? constraints,
        CancellationToken cancellationToken);

    Task<RemoteResponse?> GetResponseAsync(string commandId, CancellationToken cancellationToken);

    /// <summary>Expired commands are resent as new commands with new IDs — never re-queued (spec §15).</summary>
    Task<RemoteCommand> ResendAsNewAsync(string expiredCommandId, CancellationToken cancellationToken);
}
