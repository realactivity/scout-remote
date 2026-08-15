using System.Text;
using System.Text.Json;
using Lantern.Bridge.Protocol;
using Lantern.Bridge.Storage;
using ModelContextProtocol.Client;
using ModelContextProtocol.Protocol;
using Xunit;

namespace Lantern.Bridge.Tests;

public sealed class McpStdioSmokeTests
{
    [Fact]
    public async Task InitializeListToolsPublishStatusAndReadInbox()
    {
        using var approot = new TempDir();
        using var state = new TempDir();
        var store = new FilesystemAppFolderStore(approot.Path);
        var commandJson = """
            {
              "schemaVersion": 1,
              "commandId": "01KZYMGHG0NPD8AF5A96MP2YFW",
              "conversationId": "remote-2026-08-13",
              "type": "message",
              "createdUtc": "2026-08-15T03:00:00Z",
              "expiresUtc": "2026-08-16T03:00:00Z",
              "requestedBy": { "entraObjectId": "00000000-0000-0000-0000-000000000000" },
              "text": "seeded inbox command"
            }
            """;
        await store.WriteAsync("commands/inbox/01KZYMGHG0NPD8AF5A96MP2YFW.json", Encoding.UTF8.GetBytes(commandJson));

        var dll = Path.Combine(Path.GetDirectoryName(typeof(Lantern.Bridge.Services.BridgeService).Assembly.Location)!, "Lantern.Bridge.dll");
        Assert.True(File.Exists(dll), $"Bridge dll missing at {dll}");

        var transport = new StdioClientTransport(new StdioClientTransportOptions
        {
            Name = "lantern-bridge",
            Command = "dotnet",
            Arguments = [dll],
            EnvironmentVariables = new Dictionary<string, string?>
            {
                ["LANTERN_BACKEND"] = "filesystem",
                ["LANTERN_APPROOT"] = approot.Path,
                ["LANTERN_STATE_DIR"] = state.Path,
                ["LANTERN_AGENT_ID"] = "primary",
            },
        });

        await using var client = await McpClient.CreateAsync(transport);
        var tools = await client.ListToolsAsync();
        var names = tools.Select(t => t.Name).ToHashSet(StringComparer.Ordinal);
        foreach (var required in new[]
        {
            "publish_status", "read_inbox", "write_acknowledgement", "write_response",
            "record_processed_receipt", "read_activity", "resolve_artifact",
        })
        {
            Assert.Contains(required, names);
        }

        var status = await client.CallToolAsync("publish_status", new Dictionary<string, object?> { ["state"] = "idle" });
        var statusText = TextOf(status);
        Assert.Contains("\"lastSeenUtc\"", statusText);
        Assert.DoesNotContain("access_token", statusText, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("Bearer ", statusText, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("eyJ", statusText);

        var inbox = await client.CallToolAsync("read_inbox", new Dictionary<string, object?>());
        var inboxText = TextOf(inbox);
        Assert.Contains("01KZYMGHG0NPD8AF5A96MP2YFW", inboxText);
        Assert.DoesNotContain("access_token", inboxText, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("refresh_token", inboxText, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("client_secret", inboxText, StringComparison.OrdinalIgnoreCase);

        var hits = SecretsScanner.Find(approot.Path);
        Assert.Empty(hits);
    }

    private static string TextOf(CallToolResult result)
    {
        var text = string.Join("\n", result.Content.OfType<TextContentBlock>().Select(b => b.Text));
        Assert.False(string.IsNullOrWhiteSpace(text));
        using var _ = JsonDocument.Parse(text.StartsWith("null", StringComparison.Ordinal) ? "null" : text);
        return text;
    }
}
