using System.Text.Json;

namespace Lantern.Bridge.Protocol;

/// <summary>
/// Mutable bridge state under LANTERN_STATE_DIR: receipt mirror, delta cursor,
/// diagnostics. Never MSAL caches (spec §10, Program.cs header).
/// </summary>
public sealed class BridgeState
{
    private readonly string _root;

    public BridgeState(string root)
    {
        _root = root;
        Directory.CreateDirectory(_root);
        Directory.CreateDirectory(Path.Combine(_root, "receipts"));
        Directory.CreateDirectory(Path.Combine(_root, "diagnostics"));
    }

    public string Root => _root;

    public static string ResolveDirectory(string? configured)
    {
        if (!string.IsNullOrWhiteSpace(configured)
            && !configured.Contains("${", StringComparison.Ordinal)
            && !configured.Contains("%{", StringComparison.Ordinal))
        {
            return Path.GetFullPath(configured);
        }

        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        if (string.IsNullOrWhiteSpace(appData))
        {
            appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".config");
        }

        return Path.Combine(appData, "lantern", "state");
    }

    public bool HasReceipt(string commandId) =>
        File.Exists(ReceiptPath(commandId));

    public void WriteReceiptMirror(string commandId, string disposition, DateTimeOffset processedUtc)
    {
        var payload = JsonSerializer.Serialize(new Dictionary<string, string>
        {
            ["commandId"] = commandId,
            ["disposition"] = disposition,
            ["processedUtc"] = processedUtc.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        });
        File.WriteAllText(ReceiptPath(commandId), payload);
    }

    public void AppendJournal(string commandId, string step, DateTimeOffset utc)
    {
        var line = JsonSerializer.Serialize(new Dictionary<string, string>
        {
            ["utc"] = utc.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss.FFFFFFF'Z'"),
            ["commandId"] = commandId,
            ["step"] = step,
        });
        var path = Path.Combine(_root, "diagnostics", "journal.jsonl");
        File.AppendAllText(path, line + Environment.NewLine);
    }

    public IReadOnlyList<JournalEntry> ReadJournal()
    {
        var path = Path.Combine(_root, "diagnostics", "journal.jsonl");
        if (!File.Exists(path))
        {
            return [];
        }

        var entries = new List<JournalEntry>();
        foreach (var line in File.ReadAllLines(path))
        {
            if (string.IsNullOrWhiteSpace(line))
            {
                continue;
            }

            using var doc = JsonDocument.Parse(line);
            entries.Add(new JournalEntry(
                doc.RootElement.GetProperty("utc").GetString() ?? "",
                doc.RootElement.GetProperty("commandId").GetString() ?? "",
                doc.RootElement.GetProperty("step").GetString() ?? ""));
        }

        return entries;
    }

    public string? ReadDeltaCursor()
    {
        var path = Path.Combine(_root, "delta-cursor.txt");
        return File.Exists(path) ? File.ReadAllText(path).Trim() : null;
    }

    public void WriteDeltaCursor(string cursor) =>
        File.WriteAllText(Path.Combine(_root, "delta-cursor.txt"), cursor);

    private string ReceiptPath(string commandId) =>
        Path.Combine(_root, "receipts", commandId + ".json");
}

public sealed record JournalEntry(string Utc, string CommandId, string Step);
