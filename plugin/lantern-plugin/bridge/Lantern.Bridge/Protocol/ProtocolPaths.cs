namespace Lantern.Bridge.Protocol;

/// <summary>App Folder paths from contracts/README.md (spec §6).</summary>
public static class ProtocolPaths
{
    public const string Protocol = "protocol.json";
    public const string Inbox = "commands/inbox";
    public const string Processed = "commands/processed";
    public const string Responses = "responses";
    public const string Conversations = "conversations";
    public const string Activity = "activity";

    public static string Status(string agentId) => $"agents/{agentId}/status.json";

    public static string InboxCommand(string commandId) => $"{Inbox}/{commandId}.json";

    public static string Receipt(string commandId) => $"{Processed}/{commandId}.json";

    public static string Response(string commandId) => $"{Responses}/{commandId}.json";

    public static string ConversationEvent(string conversationId, string eventId) =>
        $"{Conversations}/{conversationId}/{eventId}.json";

    public static string ActivityFolder(DateTimeOffset occurredUtc) =>
        $"{Activity}/{occurredUtc.ToUniversalTime():yyyy-MM-dd}";

    public static string ActivityFolder(string day) => $"{Activity}/{day}";

    public static string ActivityEvent(DateTimeOffset occurredUtc, string eventId) =>
        $"{ActivityFolder(occurredUtc)}/{eventId}.json";
}
