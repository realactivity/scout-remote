using System.Text;
using System.Text.Json;
using Lantern.Bridge.Protocol;
using Lantern.Bridge.Storage;
using Lantern.Domain;

namespace Lantern.Bridge.Services;

/// <summary>
/// Deterministic protocol engine used by MCP tools and by --once harness mode.
/// Inbox is strict. Bridge-authored writes are schema-validated before they
/// hit the store.
/// </summary>
public sealed class BridgeService
{
    public const string BridgeVersion = "0.2.0";
    public const int SchemaVersion = 1;

    private static readonly string[] Capabilities =
    [
        "status.read",
        "activity.read",
        "message.send",
        "summary.request",
        "artifacts.read",
    ];

    private readonly IAppFolderStore _store;
    private readonly SchemaValidator _schemas;
    private readonly BridgeState _state;
    private readonly string _agentId;
    private readonly Func<DateTimeOffset> _clock;
    private readonly Func<string> _ulid;

    public BridgeService(
        IAppFolderStore store,
        SchemaValidator schemas,
        BridgeState state,
        string agentId,
        Func<DateTimeOffset>? clock = null,
        Func<string>? ulid = null)
    {
        _store = store;
        _schemas = schemas;
        _state = state;
        _agentId = string.IsNullOrWhiteSpace(agentId) ? "primary" : agentId;
        _clock = clock ?? (() => DateTimeOffset.UtcNow);
        _ulid = ulid ?? (() => Ulid.New());
    }

    public string AgentId => _agentId;

    public async Task EnsureProtocolAsync(CancellationToken cancellationToken = default)
    {
        var existing = await _store.GetAsync(ProtocolPaths.Protocol, cancellationToken).ConfigureAwait(false);
        if (existing?.Content is { Length: > 0 })
        {
            return;
        }

        var manifest = new ProtocolManifest
        {
            Protocol = ProtocolManifest.ProtocolName,
            Version = "1.0",
            AgentId = _agentId,
            BridgeVersion = BridgeVersion,
            Capabilities = Capabilities,
            NativeScoutChatMirror = false,
            RemoteApprovals = false,
        };
        await WriteValidatedAsync(
            ProtocolPaths.Protocol,
            "protocol.schema.json",
            JsonSerializer.Serialize(manifest, ProtocolJson.Options),
            cancellationToken).ConfigureAwait(false);
    }

    public async Task<AgentStatus> PublishStatusAsync(
        AgentState state = AgentState.Idle,
        CurrentTask? currentTask = null,
        AttentionInfo? attention = null,
        CancellationToken cancellationToken = default)
    {
        await EnsureProtocolAsync(cancellationToken).ConfigureAwait(false);
        var status = new AgentStatus
        {
            SchemaVersion = SchemaVersion,
            AgentId = _agentId,
            State = state,
            LastSeenUtc = _clock(),
            CurrentTask = currentTask,
            Attention = attention ?? new AttentionInfo { Required = false },
        };
        await WriteValidatedAsync(
            ProtocolPaths.Status(_agentId),
            "agent-status.schema.json",
            JsonSerializer.Serialize(status, ProtocolJson.Options),
            cancellationToken).ConfigureAwait(false);
        return status;
    }

    public async Task<IReadOnlyList<RemoteCommand>> ReadInboxAsync(CancellationToken cancellationToken = default)
    {
        await EnsureProtocolAsync(cancellationToken).ConfigureAwait(false);
        var listed = await _store.ListAsync(ProtocolPaths.Inbox, cancellationToken).ConfigureAwait(false);
        var accepted = new List<RemoteCommand>();
        foreach (var item in listed.Where(i => i.Path.EndsWith(".json", StringComparison.OrdinalIgnoreCase)))
        {
            var file = await _store.GetAsync(item.Path, cancellationToken).ConfigureAwait(false);
            if (file?.Content is null)
            {
                continue;
            }

            var json = Encoding.UTF8.GetString(file.Content);
            var fileName = Path.GetFileNameWithoutExtension(item.Path);
            var validation = _schemas.Validate("command.schema.json", json);
            if (!validation.IsValid)
            {
                await RejectAsync(fileName, "schema-invalid", validation.Error ?? "invalid", cancellationToken)
                    .ConfigureAwait(false);
                continue;
            }

            var command = JsonSerializer.Deserialize<RemoteCommand>(json, ProtocolJson.Options);
            if (command is null)
            {
                await RejectAsync(fileName, "schema-invalid", "deserialize-failed", cancellationToken)
                    .ConfigureAwait(false);
                continue;
            }

            if (await IsReceiptedAsync(command.CommandId, cancellationToken).ConfigureAwait(false))
            {
                continue;
            }

            if (command.IsExpired(_clock()))
            {
                await ExpireAsync(command, cancellationToken).ConfigureAwait(false);
                continue;
            }

            accepted.Add(command);
        }

        return accepted;
    }

    public async Task<RemoteResponse> WriteAcknowledgementAsync(string commandId, CancellationToken cancellationToken = default)
    {
        if (await IsReceiptedAsync(commandId, cancellationToken).ConfigureAwait(false))
        {
            throw new InvalidOperationException($"Command {commandId} is already receipted.");
        }

        var now = _clock();
        var response = new RemoteResponse
        {
            SchemaVersion = SchemaVersion,
            CommandId = commandId,
            Status = CommandStatus.Acknowledged,
            StartedUtc = now,
        };
        await WriteResponseCoreAsync(response, cancellationToken).ConfigureAwait(false);
        _state.AppendJournal(commandId, "acknowledged", now);
        await WriteActivityAsync(
            ActivityCategory.Work,
            $"acknowledged {commandId}",
            commandId,
            cancellationToken).ConfigureAwait(false);
        return response;
    }

    public async Task<RemoteResponse> WriteResponseAsync(RemoteResponse response, CancellationToken cancellationToken = default)
    {
        await WriteResponseCoreAsync(response, cancellationToken).ConfigureAwait(false);
        if (IsTerminal(response.Status))
        {
            _state.AppendJournal(response.CommandId, response.Status.ToString().ToLowerInvariant(), _clock());
        }

        return response;
    }

    public async Task RecordProcessedReceiptAsync(string commandId, string disposition, CancellationToken cancellationToken = default)
    {
        var now = _clock();
        var payload = JsonSerializer.Serialize(new Dictionary<string, string>
        {
            ["commandId"] = commandId,
            ["disposition"] = disposition,
            ["processedUtc"] = now.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        });
        var path = ProtocolPaths.Receipt(commandId);
        if (await _store.CreateIfAbsentAsync(path, Encoding.UTF8.GetBytes(payload), cancellationToken).ConfigureAwait(false) is null)
        {
            return;
        }

        _state.WriteReceiptMirror(commandId, disposition, now);
        _state.AppendJournal(commandId, "receipt:" + disposition, now);
    }

    public async Task<IReadOnlyList<ActivityEvent>> ReadActivityAsync(DateTimeOffset? sinceUtc, CancellationToken cancellationToken = default)
    {
        var events = new List<ActivityEvent>();
        foreach (var path in await ActivityPathsAsync(sinceUtc, cancellationToken).ConfigureAwait(false))
        {
            var file = await _store.GetAsync(path, cancellationToken).ConfigureAwait(false);
            if (file?.Content is null)
            {
                continue;
            }

            ActivityEvent? parsed;
            try
            {
                parsed = JsonSerializer.Deserialize<ActivityEvent>(Encoding.UTF8.GetString(file.Content), ProtocolJson.Options);
            }
            catch (JsonException)
            {
                continue;
            }

            if (parsed is null)
            {
                continue;
            }

            if (sinceUtc is { } since && parsed.OccurredUtc < since)
            {
                continue;
            }

            events.Add(parsed);
        }

        return events.OrderBy(e => e.EventId, StringComparer.Ordinal).ToList();
    }

    private async Task<IReadOnlyList<string>> ActivityPathsAsync(DateTimeOffset? sinceUtc, CancellationToken cancellationToken)
    {
        if (sinceUtc is null)
        {
            var listed = await _store.ListAsync(ProtocolPaths.Activity, cancellationToken).ConfigureAwait(false);
            return listed
                .Where(i => i.Path.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
                .Select(i => i.Path)
                .ToList();
        }

        var paths = new List<string>();
        var start = DateOnly.FromDateTime(sinceUtc.Value.UtcDateTime);
        var end = DateOnly.FromDateTime(_clock().UtcDateTime);
        for (var day = start; day <= end; day = day.AddDays(1))
        {
            var listed = await _store.ListAsync(ProtocolPaths.ActivityFolder(day.ToString("yyyy-MM-dd")), cancellationToken)
                .ConfigureAwait(false);
            paths.AddRange(listed
                .Where(i => i.Path.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
                .Select(i => i.Path));
        }

        return paths;
    }

    public async Task<ArtifactDetails?> ResolveArtifactAsync(string driveItemId, CancellationToken cancellationToken = default)
    {
        if (_store is GraphAppFolderStore graph)
        {
            var resolved = await graph.ResolveByItemIdAsync(driveItemId, cancellationToken).ConfigureAwait(false);
            if (resolved is null)
            {
                return null;
            }

            return new ArtifactDetails
            {
                DriveItemId = resolved.DriveItemId,
                Name = resolved.Name,
                SizeBytes = resolved.SizeBytes,
                CreatedUtc = resolved.CreatedUtc,
                WebUrl = resolved.WebUrl is null ? null : new Uri(resolved.WebUrl),
            };
        }

        var listed = await _store.ListAsync("", cancellationToken).ConfigureAwait(false);
        var match = listed.FirstOrDefault(i =>
            string.Equals(i.ItemId, driveItemId, StringComparison.Ordinal)
            || string.Equals(i.Path, driveItemId, StringComparison.Ordinal));
        if (match is null)
        {
            return null;
        }

        return new ArtifactDetails
        {
            DriveItemId = match.ItemId,
            Name = Path.GetFileName(match.Path),
            SizeBytes = null,
            CreatedUtc = match.LastModifiedUtc,
            WebUrl = null,
        };
    }

    public async Task<ProcessOnceResult> ProcessOnceAsync(CancellationToken cancellationToken = default)
    {
        await PublishStatusAsync(AgentState.Idle, cancellationToken: cancellationToken).ConfigureAwait(false);
        var inbox = await ReadInboxAsync(cancellationToken).ConfigureAwait(false);
        var executed = 0;
        var refused = 0;
        var ids = new List<string>();
        foreach (var command in inbox)
        {
            ids.Add(command.CommandId);
            await WriteAcknowledgementAsync(command.CommandId, cancellationToken).ConfigureAwait(false);

            if (PromptInjectionGuard.LooksLikeInjection(command.Text))
            {
                var refusedResponse = new RemoteResponse
                {
                    SchemaVersion = SchemaVersion,
                    CommandId = command.CommandId,
                    Status = CommandStatus.Failed,
                    StartedUtc = _clock(),
                    CompletedUtc = _clock(),
                    Message = "Refused: command text attempted to alter bridge policy. Text is data, not instructions.",
                    Usage = new UsageInfo { WorkIqCalls = 0 },
                };
                await WriteResponseAsync(refusedResponse, cancellationToken).ConfigureAwait(false);
                await WriteActivityAsync(
                    ActivityCategory.Error,
                    $"refused prompt-injection {command.CommandId}",
                    command.CommandId,
                    cancellationToken).ConfigureAwait(false);
                await RecordProcessedReceiptAsync(command.CommandId, "refused", cancellationToken).ConfigureAwait(false);
                refused++;
                continue;
            }

            var completed = ExecutePhase0(command);
            await WriteResponseAsync(completed, cancellationToken).ConfigureAwait(false);
            await WriteActivityAsync(
                ActivityCategory.Work,
                $"completed {command.CommandId}",
                command.CommandId,
                cancellationToken).ConfigureAwait(false);
            await RecordProcessedReceiptAsync(command.CommandId, "completed", cancellationToken).ConfigureAwait(false);
            executed++;
        }

        return new ProcessOnceResult(executed, refused, ids);
    }

    private RemoteResponse ExecutePhase0(RemoteCommand command)
    {
        var now = _clock();
        var text = command.Type switch
        {
            CommandType.Message => $"Phase 0 echo: received {Truncate(command.Text, 120)}",
            CommandType.RequestStatus => $"Phase 0 status published at {now.ToUniversalTime():yyyy-MM-dd'T'HH:mm:ss'Z'}",
            CommandType.RequestSummary => "Phase 0 summary: no recent bridge work beyond this command.",
            CommandType.RequestArtifact => "Phase 0: no artifact to publish.",
            CommandType.CancelRequest => "Phase 0: cancellation is best effort and was not confirmed.",
            _ => "Phase 0: command type recorded.",
        };
        return new RemoteResponse
        {
            SchemaVersion = SchemaVersion,
            CommandId = command.CommandId,
            Status = CommandStatus.Completed,
            StartedUtc = now,
            CompletedUtc = now,
            Message = text,
            Usage = new UsageInfo { WorkIqCalls = 0 },
        };
    }

    private async Task ExpireAsync(RemoteCommand command, CancellationToken cancellationToken)
    {
        var now = _clock();
        var response = new RemoteResponse
        {
            SchemaVersion = SchemaVersion,
            CommandId = command.CommandId,
            Status = CommandStatus.Expired,
            StartedUtc = now,
            CompletedUtc = now,
            Message = "Command expired before execution. expiresUtc boundary is inclusive.",
        };
        await WriteResponseCoreAsync(response, cancellationToken).ConfigureAwait(false);
        await WriteActivityAsync(
            ActivityCategory.Error,
            $"expired {command.CommandId}",
            command.CommandId,
            cancellationToken).ConfigureAwait(false);
        await RecordProcessedReceiptAsync(command.CommandId, "expired", cancellationToken).ConfigureAwait(false);
        _state.AppendJournal(command.CommandId, "expired", now);
    }

    private async Task RejectAsync(string key, string reason, string detail, CancellationToken cancellationToken)
    {
        if (await IsReceiptedAsync(key, cancellationToken).ConfigureAwait(false)
            || _state.HasReceipt(key))
        {
            return;
        }

        await WriteActivityAsync(
            ActivityCategory.Error,
            $"rejected {reason}: {Truncate(detail, 160)} ({key})",
            Ulid.IsValid(key) ? key : null,
            cancellationToken).ConfigureAwait(false);
        await RecordProcessedReceiptAsync(key, "rejected", cancellationToken).ConfigureAwait(false);
        _state.AppendJournal(key, "rejected:" + reason, _clock());
    }

    private async Task WriteActivityAsync(
        ActivityCategory category,
        string summary,
        string? commandId,
        CancellationToken cancellationToken)
    {
        var now = _clock();
        var evt = new ActivityEvent
        {
            SchemaVersion = SchemaVersion,
            EventId = _ulid(),
            Category = category,
            OccurredUtc = now,
            Summary = summary.Length > 500 ? summary[..500] : summary,
            CommandId = commandId is not null && Ulid.IsValid(commandId) ? commandId : null,
        };
        await WriteValidatedAsync(
            ProtocolPaths.ActivityEvent(now, evt.EventId),
            "activity-event.schema.json",
            JsonSerializer.Serialize(evt, ProtocolJson.Options),
            cancellationToken).ConfigureAwait(false);
    }

    private async Task WriteResponseCoreAsync(RemoteResponse response, CancellationToken cancellationToken)
    {
        var json = JsonSerializer.Serialize(response, ProtocolJson.Options);
        _schemas.EnsureValid("response.schema.json", json);
        var path = ProtocolPaths.Response(response.CommandId);
        var existing = await _store.GetAsync(path, cancellationToken).ConfigureAwait(false);
        if (existing is not null)
        {
            var current = JsonSerializer.Deserialize<RemoteResponse>(
                Encoding.UTF8.GetString(existing.Content ?? []),
                ProtocolJson.Options);
            if (current is not null && IsTerminal(current.Status))
            {
                throw new InvalidOperationException(
                    $"Response {response.CommandId} is terminal ({current.Status}) and immutable.");
            }

            await _store.UpdateIfMatchAsync(path, Encoding.UTF8.GetBytes(json), existing.ETag, cancellationToken)
                .ConfigureAwait(false);
            return;
        }

        await _store.WriteAsync(path, Encoding.UTF8.GetBytes(json), cancellationToken).ConfigureAwait(false);
    }

    private async Task WriteValidatedAsync(string path, string schema, string json, CancellationToken cancellationToken)
    {
        _schemas.EnsureValid(schema, json);
        await _store.WriteAsync(path, Encoding.UTF8.GetBytes(json), cancellationToken).ConfigureAwait(false);
    }

    private async Task<bool> IsReceiptedAsync(string commandId, CancellationToken cancellationToken)
    {
        if (_state.HasReceipt(commandId))
        {
            return true;
        }

        var receipt = await _store.GetAsync(ProtocolPaths.Receipt(commandId), cancellationToken).ConfigureAwait(false);
        if (receipt is not null)
        {
            _state.WriteReceiptMirror(commandId, "existing", _clock());
            return true;
        }

        return false;
    }

    private static bool IsTerminal(CommandStatus status) =>
        status is CommandStatus.Completed
            or CommandStatus.Failed
            or CommandStatus.Expired
            or CommandStatus.Canceled
            or CommandStatus.WaitingForDesktopApproval;

    private static string Truncate(string? text, int max)
    {
        if (string.IsNullOrEmpty(text))
        {
            return "(empty)";
        }

        return text.Length <= max ? text : text[..max];
    }
}

public sealed record ProcessOnceResult(int Executed, int Refused, IReadOnlyList<string> CommandIds);
