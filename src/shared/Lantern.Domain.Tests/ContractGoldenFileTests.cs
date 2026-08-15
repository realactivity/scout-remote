using System.Text.Json;
using Xunit;

namespace Lantern.Domain.Tests;

/// <summary>
/// Golden-file contract tests: every canonical payload in contracts/examples must decode
/// into the domain model and re-encode stably (decode, encode, decode, encode produces
/// identical JSON). The mobile client runs the same fixtures against the Kotlin domain
/// model (contracts/README.md rule 5). CI separately validates the fixtures against the
/// JSON Schemas, so together the two suites prove both models speak the same wire format.
/// </summary>
public sealed class ContractGoldenFileTests
{
    private static readonly JsonSerializerOptions Json = ProtocolJson.Options;

    private static readonly string ExamplesDir = FindExamplesDir();

    private static string FindExamplesDir()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null && !Directory.Exists(Path.Combine(dir.FullName, "contracts", "examples")))
        {
            dir = dir.Parent;
        }
        return dir is null
            ? throw new DirectoryNotFoundException($"contracts/examples not found above {AppContext.BaseDirectory}")
            : Path.Combine(dir.FullName, "contracts", "examples");
    }

    private static string Read(string name) => File.ReadAllText(Path.Combine(ExamplesDir, name));

    /// <summary>Decode, encode, decode, encode; the two encodings must be identical.</summary>
    private static T RoundTrip<T>(string name)
    {
        var first = JsonSerializer.Deserialize<T>(Read(name), Json)!;
        var firstEncoded = JsonSerializer.Serialize(first, Json);
        var second = JsonSerializer.Deserialize<T>(firstEncoded, Json)!;
        var secondEncoded = JsonSerializer.Serialize(second, Json);
        Assert.True(firstEncoded == secondEncoded, $"round trip of {name} must be lossless");
        return first;
    }

    [Fact]
    public void ProtocolHandshakeDecodesAndIsCompatible()
    {
        var protocol = RoundTrip<ProtocolManifest>("protocol.json");
        Assert.Equal(ProtocolManifest.ProtocolName, protocol.Protocol);
        Assert.True(protocol.IsCompatible);
        Assert.False(protocol.RemoteApprovals, "no remote approvals in MVP (spec §11)");
        Assert.Equal(5, protocol.Capabilities.Count);
    }

    [Fact]
    public void IncompatibleMajorVersionIsRejected()
    {
        var protocol = RoundTrip<ProtocolManifest>("protocol.json");
        Assert.False((protocol with { Version = "2.0" }).IsCompatible);
        Assert.False((protocol with { Protocol = "other" }).IsCompatible);
    }

    [Fact]
    public void AgentStatusDecodesWithDerivedStaleness()
    {
        var status = RoundTrip<AgentStatus>("status.json");
        Assert.Equal(AgentState.Working, status.State);
        Assert.Equal("Reviewing architecture notes", status.CurrentTask?.Title);
        Assert.False(status.Attention.Required);

        var threshold = TimeSpan.FromMinutes(30);
        Assert.False(status.IsStale(threshold, status.LastSeenUtc + TimeSpan.FromMinutes(29)));
        Assert.True(status.IsStale(threshold, status.LastSeenUtc + TimeSpan.FromMinutes(31)));
    }

    [Fact]
    public void MessageCommandDecodesWithExpirySemantics()
    {
        var command = RoundTrip<RemoteCommand>("command.message.json");
        Assert.Equal(CommandType.Message, command.Type);
        Assert.Equal(1, command.SchemaVersion);
        Assert.False(command.Constraints?.AllowSensitiveActions);

        Assert.False(command.IsExpired(command.ExpiresUtc - TimeSpan.FromSeconds(1)));
        Assert.True(command.IsExpired(command.ExpiresUtc), "expiry boundary is inclusive");
    }

    [Fact]
    public void CompletedResponseDecodesWithArtifactsAndUsage()
    {
        var response = RoundTrip<RemoteResponse>("response.completed.json");
        Assert.Equal(CommandStatus.Completed, response.Status);
        Assert.Single(response.Artifacts);
        Assert.Equal("architecture-review.docx", response.Artifacts[0].Name);
        Assert.Equal(0, response.Usage?.WorkIqCalls);
        Assert.Null(response.Usage?.ModelTokens);
    }

    [Fact]
    public void ConversationEventDecodesAndReferencesItsCommand()
    {
        var conversationEvent = RoundTrip<ConversationEvent>("conversation-event.response.json");
        Assert.Equal(ConversationEventType.Response, conversationEvent.Type);
        var command = JsonSerializer.Deserialize<RemoteCommand>(Read("command.message.json"), Json)!;
        Assert.Equal(command.CommandId, conversationEvent.CommandId);
        Assert.Equal(command.ConversationId, conversationEvent.ConversationId);
    }

    [Fact]
    public void ActivityEventDecodesWithWireCategoryNames()
    {
        var activityEvent = RoundTrip<ActivityEvent>("activity-event.work.json");
        Assert.Equal(ActivityCategory.Work, activityEvent.Category);
        Assert.False(string.IsNullOrWhiteSpace(activityEvent.Summary));
    }

    [Fact]
    public void EnumsUseSnakeCaseWireNames()
    {
        Assert.Equal("\"waiting_for_desktop\"", JsonSerializer.Serialize(AgentState.WaitingForDesktop, Json));
        Assert.Equal("\"waiting_for_desktop_approval\"", JsonSerializer.Serialize(CommandStatus.WaitingForDesktopApproval, Json));
        Assert.Equal("\"cancel_request\"", JsonSerializer.Serialize(CommandType.CancelRequest, Json));
        Assert.Equal("\"work_iq\"", JsonSerializer.Serialize(ActivityCategory.WorkIq, Json));
    }

    [Fact]
    public void OptionalAbsentFieldsStayAbsentOnEncode()
    {
        var command = JsonSerializer.Deserialize<RemoteCommand>(Read("command.message.json"), Json)!;
        var encoded = JsonSerializer.Serialize(command, Json);
        Assert.DoesNotContain("targetCommandId", encoded);
    }

    [Fact]
    public void TimestampsRoundTripAsUtcWithZSuffix()
    {
        var status = JsonSerializer.Deserialize<AgentStatus>(Read("status.json"), Json)!;
        Assert.Equal(DateTimeOffset.Parse("2026-08-13T22:32:10Z"), status.LastSeenUtc);
        Assert.Contains("\"lastSeenUtc\":\"2026-08-13T22:32:10Z\"", JsonSerializer.Serialize(status, Json));
    }

    [Fact]
    public void TolerantReaderIgnoresUnknownFieldsFromANewerBridge()
    {
        // Compatibility policy (spec §15.2): bridge-authored files may gain optional
        // fields in a minor revision; deployed readers must keep working. (The strict
        // command-inbox gate is schema validation in the bridge, not the deserializer.)
        var withUnknownField = Read("status.json").Replace(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1, \"introducedInAMinorRevision\": {\"nested\": true},");
        var status = JsonSerializer.Deserialize<AgentStatus>(withUnknownField, Json)!;
        Assert.Equal(AgentState.Working, status.State);
    }
}
