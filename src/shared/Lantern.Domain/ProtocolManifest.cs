using System.Text.Json.Serialization;

namespace Lantern.Domain;

/// <summary>
/// Bridge-published protocol handshake (protocol.json).
/// Contract: contracts/schemas/protocol.schema.json.
/// </summary>
public sealed record ProtocolManifest
{
    public const string ProtocolName = "lantern";

    /// <summary>Major version this client implements; stop processing an incompatible major (spec §15).</summary>
    public const int SupportedMajorVersion = 1;

    public required string Protocol { get; init; }
    public required string Version { get; init; }
    public required string AgentId { get; init; }
    public required string BridgeVersion { get; init; }
    public required IReadOnlyList<string> Capabilities { get; init; }
    public bool NativeScoutChatMirror { get; init; }
    public bool RemoteApprovals { get; init; }

    [JsonIgnore]
    public bool IsCompatible =>
        Protocol == ProtocolName &&
        int.TryParse(Version.Split('.')[0], out var major) &&
        major == SupportedMajorVersion;
}
