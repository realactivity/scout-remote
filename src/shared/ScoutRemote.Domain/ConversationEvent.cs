namespace ScoutRemote.Domain;

/// <summary>
/// Append-only bridge-owned remote-thread event
/// (conversations/{conversationId}/{eventId}.json, schemaVersion 1).
/// Immutable once written. Command/response events reference their protocol files by
/// CommandId rather than duplicating content, so the thread is reproducible from
/// protocol data (spec §8.1). Contract: contracts/schemas/conversation-event.schema.json.
/// </summary>
public sealed record ConversationEvent
{
    public required int SchemaVersion { get; init; }

    /// <summary>ULID. Unique and time-sortable. The stream is ordered by EventId.</summary>
    public required string EventId { get; init; }

    public required string ConversationId { get; init; }
    public required ConversationEventType Type { get; init; }
    public required DateTimeOffset OccurredUtc { get; init; }

    /// <summary>For Command/Response events: the protocol file holding the content.</summary>
    public string? CommandId { get; init; }

    /// <summary>For Artifact events.</summary>
    public ArtifactReference? Artifact { get; init; }

    /// <summary>For Attention events: exactly what the user must do on the desktop.</summary>
    public string? AttentionReason { get; init; }
}

public enum ConversationEventType
{
    Command,
    Response,
    Artifact,
    Attention,
}
