namespace Lantern.Bridge.Storage;

/// <summary>
/// Port for the OneDrive App Folder mailbox (spec §6, §16.2). Both the Graph
/// backend and the local filesystem harness implement this surface so offline
/// tests and the real tenant path share one protocol engine.
/// </summary>
public interface IAppFolderStore
{
    Task<StoredItem?> GetAsync(string path, CancellationToken cancellationToken = default);

    Task<IReadOnlyList<StoredItem>> ListAsync(string prefix, CancellationToken cancellationToken = default);

    Task<StoredItem> WriteAsync(string path, byte[] content, CancellationToken cancellationToken = default);

    /// <summary>
    /// Create only if the path is absent. Returns null when the file already
    /// exists (create-if-absent enqueue keyed on commandId, spec §16.2).
    /// </summary>
    Task<StoredItem?> CreateIfAbsentAsync(string path, byte[] content, CancellationToken cancellationToken = default);

    Task<StoredItem> UpdateIfMatchAsync(string path, byte[] content, string etag, CancellationToken cancellationToken = default);

    Task DeleteAsync(string path, CancellationToken cancellationToken = default);

    Task<DeltaPage> ListDeltaAsync(string? cursor, CancellationToken cancellationToken = default);
}

public sealed record DeltaPage(IReadOnlyList<StoredItem> Items, string? Cursor);
