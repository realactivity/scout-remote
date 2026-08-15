using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Lantern.Domain;

/// <summary>
/// The single JSON configuration for all protocol I/O on the bridge side. Mirrors the
/// Kotlin ProtocolJson so both domain models produce identical wire JSON against
/// contracts/schemas.
///
/// Compatibility policy (spec §15.2): tolerant reader, strict writer.
/// - Writing: camelCase properties, snake_case string enums, absent optional fields
///   stay absent (nulls are not written), timestamps as UTC ISO 8601 with a Z suffix.
/// - Reading: unknown fields are ignored (System.Text.Json default). Note this is the
///   deserializer only; the bridge's command inbox additionally validates raw command
///   JSON against contracts/schemas/command.schema.json and rejects, without executing,
///   anything that fails strict validation, including unknown fields.
/// </summary>
public static class ProtocolJson
{
    public static JsonSerializerOptions Options { get; } = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Converters =
        {
            new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower, allowIntegerValues: false),
            new UtcDateTimeOffsetConverter(),
        },
    };

    /// <summary>
    /// Writes timestamps as UTC with a Z suffix (e.g. 2026-08-13T22:40:00Z), matching the
    /// schemas' date-time format and the Kotlin Instant encoding. The default
    /// System.Text.Json encoding (+00:00 offset) is valid ISO 8601 but would make the two
    /// domain models produce different bytes for the same payload.
    /// </summary>
    private sealed class UtcDateTimeOffsetConverter : JsonConverter<DateTimeOffset>
    {
        public override DateTimeOffset Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options) =>
            reader.GetDateTimeOffset();

        public override void Write(Utf8JsonWriter writer, DateTimeOffset value, JsonSerializerOptions options) =>
            writer.WriteStringValue(
                value.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss.FFFFFFF'Z'", CultureInfo.InvariantCulture));
    }
}
