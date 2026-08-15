namespace Lantern.Domain;

/// <summary>
/// Bridge-to-mobile response (responses/{commandId}.json, schemaVersion 1).
/// Mutable while in flight (the bridge updates it through Acknowledged/Running); immutable once
/// it reaches a terminal status. Corrections are new events referencing the original command.
/// Contract: contracts/schemas/response.schema.json.
/// </summary>
public sealed record RemoteResponse
{
    public required int SchemaVersion { get; init; }
    public required string CommandId { get; init; }
    public required CommandStatus Status { get; init; }
    public DateTimeOffset? StartedUtc { get; init; }
    public DateTimeOffset? CompletedUtc { get; init; }
    public string? Message { get; init; }

    /// <summary>When Status is WaitingForDesktopApproval: exactly what the user must do on the desktop.</summary>
    public string? AttentionReason { get; init; }

    public IReadOnlyList<ArtifactReference> Artifacts { get; init; } = [];
    public UsageInfo? Usage { get; init; }
}

/// <summary>
/// Wire command status (spec Appendix A.1, response.schema.json).
/// The client-local states deliberately do not appear here: "draft" (unsent, cached
/// on-device) and "queued" (command written to the inbox with no response file yet) are
/// presentation states the mobile client derives locally. The first wire status is the
/// bridge's prompt acknowledgement (spec §5.1).
/// </summary>
public enum CommandStatus
{
    Acknowledged,
    Running,
    Completed,
    Failed,
    Expired,
    WaitingForDesktopApproval,

    /// <summary>Only when cancellation is confirmed by the bridge. Never claimed optimistically.</summary>
    Canceled,
}

/// <summary>Artifacts are OneDrive items referenced by ID; content is never duplicated into protocol JSON (spec §11.1).</summary>
public sealed record ArtifactReference
{
    public required string Name { get; init; }
    public required string DriveItemId { get; init; }
}

public sealed record UsageInfo
{
    public int WorkIqCalls { get; init; }
    public long? ModelTokens { get; init; }
}
