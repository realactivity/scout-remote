namespace ScoutRemote.Domain;

/// <summary>
/// Bridge-published agent status snapshot (agents/{agentId}/status.json, schemaVersion 1).
/// Contract: contracts/schemas/agent-status.schema.json.
/// </summary>
public sealed record AgentStatus
{
    public required int SchemaVersion { get; init; }
    public required string AgentId { get; init; }
    public required AgentState State { get; init; }
    public required DateTimeOffset LastSeenUtc { get; init; }
    public CurrentTask? CurrentTask { get; init; }
    public AttentionInfo Attention { get; init; } = new();

    /// <summary>
    /// Presence is evidence-based: stale/offline is derived from LastSeenUtc against a
    /// configurable threshold, never reported by the bridge itself (spec Appendix A.2).
    /// </summary>
    public bool IsStale(TimeSpan staleThreshold, DateTimeOffset nowUtc) =>
        nowUtc - LastSeenUtc > staleThreshold;
}

public sealed record CurrentTask
{
    public required string Title { get; init; }
    public DateTimeOffset? StartedUtc { get; init; }
    public string? ProgressText { get; init; }
}

public sealed record AttentionInfo
{
    public bool Required { get; init; }
    public string? Reason { get; init; }
}

/// <summary>Bridge-reported state; "stale/offline" is a client-derived overlay, not a wire value.</summary>
public enum AgentState
{
    Unknown,
    Idle,
    Working,
    WaitingForDesktop,
}
