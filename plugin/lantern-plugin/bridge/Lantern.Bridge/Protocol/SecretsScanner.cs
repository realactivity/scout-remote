using System.Text.RegularExpressions;

namespace Lantern.Bridge.Protocol;

/// <summary>
/// Walks a directory tree and fails if credential-shaped material appears.
/// Used by smoke tests after a harness run. Protocol files must never hold
/// tokens (spec §2.2, §11).
/// </summary>
public static class SecretsScanner
{
    private static readonly Regex[] Patterns =
    [
        new(@"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}", RegexOptions.Compiled),
        new(@"\bBearer\s+[A-Za-z0-9._\-+=/]{20,}", RegexOptions.Compiled | RegexOptions.IgnoreCase),
        new(@"client_secret\s*[:=]", RegexOptions.Compiled | RegexOptions.IgnoreCase),
        new(@"refresh_token\s*[:=]", RegexOptions.Compiled | RegexOptions.IgnoreCase),
        new(@"access_token\s*[:=]", RegexOptions.Compiled | RegexOptions.IgnoreCase),
        new(@"device_code\s*[:=]", RegexOptions.Compiled | RegexOptions.IgnoreCase),
        new(@"-----BEGIN [A-Z ]*PRIVATE KEY-----", RegexOptions.Compiled),
    ];

    public static IReadOnlyList<string> Find(string root)
    {
        var hits = new List<string>();
        if (!Directory.Exists(root))
        {
            return hits;
        }

        foreach (var file in Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories))
        {
            string text;
            try
            {
                text = File.ReadAllText(file);
            }
            catch (IOException)
            {
                continue;
            }

            foreach (var pattern in Patterns)
            {
                if (pattern.IsMatch(text))
                {
                    hits.Add($"{Path.GetRelativePath(root, file)}: {pattern}");
                    break;
                }
            }
        }

        return hits;
    }
}
