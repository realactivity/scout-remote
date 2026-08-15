using System.Security.Cryptography;

namespace Lantern.Bridge.Protocol;

/// <summary>
/// Crockford-base32 ULID (26 chars). Matches contracts/schemas commandId pattern.
/// </summary>
public static class Ulid
{
    public const string Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    public static string New(DateTimeOffset? now = null)
    {
        var ms = (now ?? DateTimeOffset.UtcNow).ToUnixTimeMilliseconds();
        if (ms < 0)
        {
            ms = 0;
        }

        Span<byte> entropy = stackalloc byte[10];
        RandomNumberGenerator.Fill(entropy);

        Span<char> chars = stackalloc char[26];
        EncodeTime(ms, chars);
        EncodeEntropy(entropy, chars);
        return new string(chars);
    }

    public static bool IsValid(string? value)
    {
        if (value is null || value.Length != 26)
        {
            return false;
        }

        foreach (var c in value)
        {
            if (Alphabet.IndexOf(c) < 0)
            {
                return false;
            }
        }

        return true;
    }

    private static void EncodeTime(long ms, Span<char> chars)
    {
        for (var i = 9; i >= 0; i--)
        {
            chars[i] = Alphabet[(int)(ms % 32)];
            ms /= 32;
        }
    }

    private static void EncodeEntropy(ReadOnlySpan<byte> entropy, Span<char> chars)
    {
        var acc = 0;
        var bits = 0;
        var index = 10;
        foreach (var b in entropy)
        {
            acc = (acc << 8) | b;
            bits += 8;
            while (bits >= 5 && index < 26)
            {
                bits -= 5;
                chars[index++] = Alphabet[(acc >> bits) & 31];
            }
        }

        if (index < 26 && bits > 0)
        {
            chars[index] = Alphabet[(acc << (5 - bits)) & 31];
        }
    }
}
