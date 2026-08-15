namespace Lantern.Bridge.Storage;

/// <summary>
/// One object in the App Folder mailbox. Content is present on read; list/delta
/// results may omit it. ItemId is the Graph driveItem id, or a filesystem stand-in.
/// </summary>
public sealed record StoredItem(
    string Path,
    string ETag,
    string ItemId,
    DateTimeOffset LastModifiedUtc,
    byte[]? Content = null);
