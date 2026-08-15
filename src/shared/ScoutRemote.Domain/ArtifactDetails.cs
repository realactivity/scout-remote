namespace ScoutRemote.Domain;

/// <summary>
/// Resolved OneDrive artifact metadata for the Artifacts screen (name, type, size, created,
/// open link — spec §9). Not a protocol file: the transport resolves an ArtifactReference's
/// DriveItemId against Graph on demand. Content is never copied into protocol JSON (spec §11.1).
/// </summary>
public sealed record ArtifactDetails
{
    public required string DriveItemId { get; init; }
    public required string Name { get; init; }
    public long? SizeBytes { get; init; }
    public DateTimeOffset? CreatedUtc { get; init; }

    /// <summary>OneDrive web link used to open the artifact in the appropriate Microsoft 365 app.</summary>
    public Uri? WebUrl { get; init; }
}
