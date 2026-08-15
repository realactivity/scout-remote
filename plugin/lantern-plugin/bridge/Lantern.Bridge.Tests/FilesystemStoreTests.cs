using Lantern.Bridge.Storage;
using Xunit;

namespace Lantern.Bridge.Tests;

public sealed class FilesystemStoreTests
{
    [Fact]
    public async Task CreateIfAbsentIsIdempotentAndDoesNotOverwrite()
    {
        using var dir = new TempDir();
        var store = new FilesystemAppFolderStore(dir.Path);
        var first = await store.CreateIfAbsentAsync("commands/inbox/a.json", "one"u8.ToArray());
        var second = await store.CreateIfAbsentAsync("commands/inbox/a.json", "two"u8.ToArray());
        Assert.NotNull(first);
        Assert.Null(second);
        var read = await store.GetAsync("commands/inbox/a.json");
        Assert.Equal("one"u8.ToArray(), read!.Content);
    }

    [Fact]
    public async Task UpdateIfMatchHonorsEtag()
    {
        using var dir = new TempDir();
        var store = new FilesystemAppFolderStore(dir.Path);
        var written = await store.WriteAsync("agents/primary/status.json", "v1"u8.ToArray());
        var updated = await store.UpdateIfMatchAsync("agents/primary/status.json", "v2"u8.ToArray(), written.ETag);
        Assert.NotEqual(written.ETag, updated.ETag);
        await Assert.ThrowsAsync<ETagConflictException>(() =>
            store.UpdateIfMatchAsync("agents/primary/status.json", "v3"u8.ToArray(), written.ETag));
    }

    [Fact]
    public async Task ListAndDeltaSeeNewFiles()
    {
        using var dir = new TempDir();
        var store = new FilesystemAppFolderStore(dir.Path);
        var empty = await store.ListDeltaAsync(null);
        await store.WriteAsync("protocol.json", "{}"u8.ToArray());
        var listed = await store.ListAsync("");
        Assert.Contains(listed, i => i.Path == "protocol.json");
        var delta = await store.ListDeltaAsync(empty.Cursor);
        Assert.Contains(delta.Items, i => i.Path == "protocol.json");
    }
}

internal sealed class TempDir : IDisposable
{
    public string Path { get; } = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "lantern-" + Guid.NewGuid().ToString("N"));

    public TempDir() => Directory.CreateDirectory(Path);

    public void Dispose()
    {
        try
        {
            Directory.Delete(Path, recursive: true);
        }
        catch (IOException)
        {
        }
    }
}
