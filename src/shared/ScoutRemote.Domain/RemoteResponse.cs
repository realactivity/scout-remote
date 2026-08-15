namespace ScoutRemote.Domain;

/// <summary>
/// Bridge-to-mobile response (responses/{commandId}.json, schemaVersion 1).
/// A completed response is immutable; corrections are new events referencing the original command.
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

/// <summary>Command state machine (spec Appendix A.1).</summary>
public enum CommandStatus
{
    Draft,
    Queued,
    Acknowledged,
    Running,
    Completed,
    Failed,
    Expired,
    WaitingForDesktopApproval,

    /// <summary>Only when cancellation is confirmed by the bridge — never claimed optimistically.</summary>
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
