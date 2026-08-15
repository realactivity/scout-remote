namespace Lantern.Bridge.Protocol;

/// <summary>
/// Command text is data, never instructions to the bridge (spec §11). A
/// refusal is recorded as an auditable failed response plus an activity event.
/// Patterns are deliberately simple and documented.
/// </summary>
public static class PromptInjectionGuard
{
    private static readonly string[] Phrases =
    [
        "ignore previous",
        "ignore these rules",
        "ignore all instructions",
        "disable validation",
        "skip schema",
        "skip validation",
        "grant approval",
        "grant remote approval",
        "allow sensitive",
        "expand permission",
        "you are now",
        "system prompt",
        "jailbreak",
        "override policy",
        "bypass approval",
    ];

    public static bool LooksLikeInjection(string? text)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return false;
        }

        var haystack = text.ToLowerInvariant();
        return Phrases.Any(p => haystack.Contains(p, StringComparison.Ordinal));
    }
}
