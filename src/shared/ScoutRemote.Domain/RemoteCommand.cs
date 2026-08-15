namespace ScoutRemote.Domain;

/// <summary>
/// Mobile-to-bridge command (commands/inbox/{commandId}.json, schemaVersion 1).
/// Immutable once written; idempotent via processed receipts; never executed after expiry.
/// Contract: contracts/schemas/command.schema.json.
/// </summary>
public sealed record RemoteCommand
{
    public required int SchemaVersion { get; init; }

    /// <summary>ULID. Globally unique and never reused (replay control, spec §11).</summary>
    public required string CommandId { get; init; }

    public required string ConversationId { get; init; }
    public required CommandType Type { get; init; }
    public required DateTimeOffset CreatedUtc { get; init; }
    public required DateTimeOffset ExpiresUtc { get; init; }
    public required CommandRequester RequestedBy { get; init; }

    /// <summary>Untrusted natural-language input; data, never bridge policy (spec §11).</summary>
    public string? Text { get; init; }

    /// <summary>For CancelRequest: the command whose cancellation is requested (best effort only).</summary>
    public string? TargetCommandId { get; init; }

    public CommandConstraints? Constraints { get; init; }

    public bool IsExpired(DateTimeOffset nowUtc) => nowUtc >= ExpiresUtc;
}

/// <summary>Allowed MVP command types (spec §7.1).</summary>
public enum CommandType
{
    Message,
    RequestStatus,
    RequestSummary,
    RequestArtifact,
    CancelRequest,
}

public sealed record CommandRequester
{
    public required string EntraObjectId { get; init; }
}

public sealed record CommandConstraints
{
    public int? MaxWorkIqCalls { get; init; }

    /// <summary>Always false in MVP: sensitive actions require desktop approval and cannot be granted remotely.</summary>
    public bool AllowSensitiveActions { get; init; }
}
