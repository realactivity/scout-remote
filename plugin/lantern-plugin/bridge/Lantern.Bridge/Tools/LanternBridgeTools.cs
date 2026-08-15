using System.ComponentModel;
using System.Text.Json;
using Lantern.Bridge.Services;
using Lantern.Domain;
using ModelContextProtocol.Server;

namespace Lantern.Bridge.Tools;

/// <summary>
/// Deterministic MCP tools. No tool returns credential material.
/// </summary>
[McpServerToolType]
public sealed class LanternBridgeTools
{
    private readonly BridgeService _bridge;

    public LanternBridgeTools(BridgeService bridge)
    {
        _bridge = bridge;
    }

    [McpServerTool(Name = "publish_status"), Description("Publish a fresh agent status snapshot. Presence is lastSeenUtc.")]
    public async Task<string> PublishStatus(
        [Description("Agent state: unknown, idle, working, waiting_for_desktop")] string state = "idle",
        [Description("Optional current task title")] string? currentTaskTitle = null,
        CancellationToken cancellationToken = default)
    {
        if (!Enum.TryParse<AgentState>(ToPascal(state), ignoreCase: true, out var parsed))
        {
            parsed = AgentState.Idle;
        }

        CurrentTask? task = string.IsNullOrWhiteSpace(currentTaskTitle)
            ? null
            : new CurrentTask { Title = currentTaskTitle };
        var status = await _bridge.PublishStatusAsync(parsed, task, cancellationToken: cancellationToken)
            .ConfigureAwait(false);
        return JsonSerializer.Serialize(status, ProtocolJson.Options);
    }

    [McpServerTool(Name = "read_inbox"), Description("Return schema-valid, unexpired, unreceipted inbox commands. Invalid files are rejected and never executed.")]
    public async Task<string> ReadInbox(CancellationToken cancellationToken = default)
    {
        var commands = await _bridge.ReadInboxAsync(cancellationToken).ConfigureAwait(false);
        return JsonSerializer.Serialize(commands, ProtocolJson.Options);
    }

    [McpServerTool(Name = "write_acknowledgement"), Description("Write the prompt acknowledged response for a command.")]
    public async Task<string> WriteAcknowledgement(
        [Description("Command ULID")] string commandId,
        CancellationToken cancellationToken = default)
    {
        var response = await _bridge.WriteAcknowledgementAsync(commandId, cancellationToken).ConfigureAwait(false);
        return JsonSerializer.Serialize(response, ProtocolJson.Options);
    }

    [McpServerTool(Name = "write_response"), Description("Write or update a command response. Terminal responses become immutable.")]
    public async Task<string> WriteResponse(
        [Description("Response JSON matching response.schema.json")] string responseJson,
        CancellationToken cancellationToken = default)
    {
        var response = JsonSerializer.Deserialize<RemoteResponse>(responseJson, ProtocolJson.Options)
            ?? throw new InvalidOperationException("responseJson did not deserialize.");
        var written = await _bridge.WriteResponseAsync(response, cancellationToken).ConfigureAwait(false);
        return JsonSerializer.Serialize(written, ProtocolJson.Options);
    }

    [McpServerTool(Name = "record_processed_receipt"), Description("Record an idempotency receipt so the command cannot execute again.")]
    public async Task<string> RecordProcessedReceipt(
        [Description("Command ULID")] string commandId,
        [Description("Disposition: completed, expired, rejected, refused")] string disposition = "completed",
        CancellationToken cancellationToken = default)
    {
        await _bridge.RecordProcessedReceiptAsync(commandId, disposition, cancellationToken).ConfigureAwait(false);
        return JsonSerializer.Serialize(new Dictionary<string, string>
        {
            ["commandId"] = commandId,
            ["disposition"] = disposition,
        });
    }

    [McpServerTool(Name = "read_activity"), Description("Read activity events, optionally after an ISO-8601 timestamp.")]
    public async Task<string> ReadActivity(
        [Description("Optional ISO-8601 lower bound")] string? sinceUtc = null,
        CancellationToken cancellationToken = default)
    {
        DateTimeOffset? since = null;
        if (!string.IsNullOrWhiteSpace(sinceUtc) && DateTimeOffset.TryParse(sinceUtc, out var parsed))
        {
            since = parsed;
        }

        var events = await _bridge.ReadActivityAsync(since, cancellationToken).ConfigureAwait(false);
        return JsonSerializer.Serialize(events, ProtocolJson.Options);
    }

    [McpServerTool(Name = "resolve_artifact"), Description("Resolve a driveItemId to name/size/link. Returns null when unavailable.")]
    public async Task<string> ResolveArtifact(
        [Description("OneDrive driveItem id")] string driveItemId,
        CancellationToken cancellationToken = default)
    {
        var details = await _bridge.ResolveArtifactAsync(driveItemId, cancellationToken).ConfigureAwait(false);
        return details is null ? "null" : JsonSerializer.Serialize(details, ProtocolJson.Options);
    }

    private static string ToPascal(string value)
    {
        if (string.IsNullOrEmpty(value))
        {
            return value;
        }

        var parts = value.Split('_', StringSplitOptions.RemoveEmptyEntries);
        return string.Concat(parts.Select(p => char.ToUpperInvariant(p[0]) + p[1..].ToLowerInvariant()));
    }
}
