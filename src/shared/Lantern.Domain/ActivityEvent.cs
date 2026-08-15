namespace Lantern.Domain;

/// <summary>
/// Chronological bridge activity event (activity/{yyyy-MM-dd}/{eventId}.json, schemaVersion 1).
/// Immutable once written; categories match the Activity screen filters (spec §9).
/// Contract: contracts/schemas/activity-event.schema.json.
/// </summary>
public sealed record ActivityEvent
{
    public required int SchemaVersion { get; init; }

    /// <summary>ULID; unique and time-sortable within the day folder.</summary>
    public required string EventId { get; init; }

    public required ActivityCategory Category { get; init; }
    public required DateTimeOffset OccurredUtc { get; init; }

    /// <summary>One-line human-readable event text; untrusted data at the consumer.</summary>
    public required string Summary { get; init; }

    /// <summary>Set when produced while processing a remote command (Activity groups by command).</summary>
    public string? CommandId { get; init; }

    public ArtifactReference? Artifact { get; init; }
}

public enum ActivityCategory
{
    Work,
    Artifact,
    Error,
    WorkIq,
}
