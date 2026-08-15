using System.Security.Cryptography;

namespace Lantern.Bridge.Storage;

/// <summary>
/// Local-directory App Folder. Layout matches contracts/README.md. ETags are
/// SHA-256 of file bytes. Used by every offline test and the cross-language
/// smoke harness.
/// </summary>
public sealed class FilesystemAppFolderStore : IAppFolderStore
{
    private readonly string _root;
    private readonly Lock _gate = new();

    public FilesystemAppFolderStore(string root)
    {
        _root = Path.GetFullPath(root);
        Directory.CreateDirectory(_root);
    }

    public string Root => _root;

    public Task<StoredItem?> GetAsync(string path, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var full = Map(path);
            if (!File.Exists(full))
            {
                return Task.FromResult<StoredItem?>(null);
            }

            var bytes = File.ReadAllBytes(full);
            return Task.FromResult<StoredItem?>(ToItem(path, full, bytes));
        }
    }

    public Task<IReadOnlyList<StoredItem>> ListAsync(string prefix, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var items = new List<StoredItem>();
            var prefixNorm = Normalize(prefix);
            foreach (var file in EnumerateFiles())
            {
                var rel = Relative(file);
                if (prefixNorm.Length == 0
                    || rel == prefixNorm
                    || rel.StartsWith(prefixNorm.TrimEnd('/') + "/", StringComparison.Ordinal))
                {
                    items.Add(ToItem(rel, file, content: null));
                }
            }

            return Task.FromResult<IReadOnlyList<StoredItem>>(items);
        }
    }

    public Task<StoredItem> WriteAsync(string path, byte[] content, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var full = Map(path);
            Directory.CreateDirectory(Path.GetDirectoryName(full)!);
            File.WriteAllBytes(full, content);
            return Task.FromResult(ToItem(path, full, content));
        }
    }

    public Task<StoredItem?> CreateIfAbsentAsync(string path, byte[] content, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var full = Map(path);
            Directory.CreateDirectory(Path.GetDirectoryName(full)!);
            try
            {
                using var stream = new FileStream(full, FileMode.CreateNew, FileAccess.Write, FileShare.None);
                stream.Write(content, 0, content.Length);
            }
            catch (IOException) when (File.Exists(full))
            {
                return Task.FromResult<StoredItem?>(null);
            }

            return Task.FromResult<StoredItem?>(ToItem(path, full, content));
        }
    }

    public Task<StoredItem> UpdateIfMatchAsync(string path, byte[] content, string etag, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var full = Map(path);
            if (!File.Exists(full))
            {
                throw new FileNotFoundException($"App Folder path not found: {path}", full);
            }

            var current = ETagOf(File.ReadAllBytes(full));
            if (!string.Equals(current, etag, StringComparison.Ordinal))
            {
                throw new ETagConflictException(path, etag, current);
            }

            File.WriteAllBytes(full, content);
            return Task.FromResult(ToItem(path, full, content));
        }
    }

    public Task DeleteAsync(string path, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            var full = Map(path);
            if (File.Exists(full))
            {
                File.Delete(full);
            }

            return Task.CompletedTask;
        }
    }

    public Task<DeltaPage> ListDeltaAsync(string? cursor, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_gate)
        {
            long sinceTicks = 0;
            if (!string.IsNullOrWhiteSpace(cursor) && long.TryParse(cursor, out var parsed))
            {
                sinceTicks = parsed;
            }

            var items = new List<StoredItem>();
            long maxTicks = sinceTicks;
            foreach (var file in EnumerateFiles())
            {
                var ticks = File.GetLastWriteTimeUtc(file).Ticks;
                if (ticks > sinceTicks)
                {
                    items.Add(ToItem(Relative(file), file, content: null));
                }

                if (ticks > maxTicks)
                {
                    maxTicks = ticks;
                }
            }

            return Task.FromResult(new DeltaPage(items, maxTicks.ToString()));
        }
    }

    private IEnumerable<string> EnumerateFiles()
    {
        if (!Directory.Exists(_root))
        {
            return [];
        }

        return Directory.EnumerateFiles(_root, "*", SearchOption.AllDirectories);
    }

    private string Map(string path)
    {
        var rel = Normalize(path).Replace('/', Path.DirectorySeparatorChar);
        var full = Path.GetFullPath(Path.Combine(_root, rel));
        var relative = Path.GetRelativePath(_root, full);
        if (relative == ".."
            || relative.StartsWith(".." + Path.DirectorySeparatorChar, StringComparison.Ordinal)
            || Path.IsPathRooted(relative))
        {
            throw new InvalidOperationException($"Path escapes App Folder root: {path}");
        }

        return full;
    }

    private string Relative(string fullPath)
    {
        var rel = Path.GetRelativePath(_root, fullPath);
        return Normalize(rel);
    }

    private static string Normalize(string path) =>
        path.Replace('\\', '/').TrimStart('/');

    private static StoredItem ToItem(string path, string fullPath, byte[]? content)
    {
        var bytes = content ?? File.ReadAllBytes(fullPath);
        var info = new FileInfo(fullPath);
        return new StoredItem(
            Path: Normalize(path),
            ETag: ETagOf(bytes),
            ItemId: Normalize(path),
            LastModifiedUtc: info.LastWriteTimeUtc,
            Content: content);
    }

    internal static string ETagOf(byte[] content)
    {
        var hash = SHA256.HashData(content);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }
}

public sealed class ETagConflictException : Exception
{
    public ETagConflictException(string path, string expected, string actual)
        : base($"ETag mismatch for {path}: expected {expected}, actual {actual}.")
    {
        Path = path;
        Expected = expected;
        Actual = actual;
    }

    public string Path { get; }
    public string Expected { get; }
    public string Actual { get; }
}
