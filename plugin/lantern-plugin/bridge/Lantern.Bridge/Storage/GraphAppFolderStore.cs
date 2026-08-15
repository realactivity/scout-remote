using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using Lantern.Bridge.Identity;

namespace Lantern.Bridge.Storage;

/// <summary>
/// Microsoft Graph backend against /me/drive/special/approot. Create-if-absent
/// is keyed on commandId. Mutable writes send If-Match. 429 and 503 responses
/// honor Retry-After. Exercised by the real-tenant smoke kit, not by CI.
/// </summary>
public sealed class GraphAppFolderStore : IAppFolderStore
{
    private const string GraphRoot = "https://graph.microsoft.com/v1.0";
    private readonly IBridgeIdentity _identity;
    private readonly HttpClient _http;
    private readonly int _maxRetries;
    private readonly Func<TimeSpan, CancellationToken, Task> _delay;
    private readonly Func<DateTimeOffset> _clock;
    private string? _approotId;
    private string? _driveId;
    private string? _approotPath;

    public GraphAppFolderStore(
        IBridgeIdentity identity,
        HttpClient? http = null,
        int maxRetries = 4,
        Func<TimeSpan, CancellationToken, Task>? delay = null,
        Func<DateTimeOffset>? clock = null)
    {
        _identity = identity;
        _http = http ?? new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        _maxRetries = maxRetries;
        _delay = delay ?? ((wait, cancellationToken) => Task.Delay(wait, cancellationToken));
        _clock = clock ?? (() => DateTimeOffset.UtcNow);
    }

    public async Task<StoredItem?> GetAsync(string path, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        using var response = await SendAsync(
            HttpMethod.Get,
            ItemByPathUri(path) + ":/content",
            null,
            null,
            false,
            cancellationToken).ConfigureAwait(false);
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }

        response.EnsureSuccessStatusCode();
        var bytes = await response.Content.ReadAsByteArrayAsync(cancellationToken).ConfigureAwait(false);
        var meta = await GetMetadataAsync(path, cancellationToken).ConfigureAwait(false);
        return new StoredItem(
            Path: Normalize(path),
            ETag: meta?.ETag ?? ETagFallback(bytes),
            ItemId: meta?.ItemId ?? Normalize(path),
            LastModifiedUtc: meta?.LastModifiedUtc ?? DateTimeOffset.UtcNow,
            Content: bytes);
    }

    public async Task<IReadOnlyList<StoredItem>> ListAsync(string prefix, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        var prefixNorm = Normalize(prefix);
        var folder = string.IsNullOrEmpty(prefixNorm) ? string.Empty : prefixNorm;
        var uri = string.IsNullOrEmpty(folder)
            ? $"{GraphRoot}/drives/{_driveId}/items/{_approotId}/children"
            : ItemByPathUri(folder) + ":/children";

        var items = new List<StoredItem>();
        await foreach (var child in EnumerateChildrenAsync(uri, folder, cancellationToken).ConfigureAwait(false))
        {
            items.Add(child);
        }

        return items;
    }

    public async Task<StoredItem> WriteAsync(string path, byte[] content, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        using var response = await SendAsync(
            HttpMethod.Put,
            ItemByPathUri(path) + ":/content",
            content,
            null,
            false,
            cancellationToken).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var meta = await ParseItemAsync(response, path, content).ConfigureAwait(false);
        return meta;
    }

    public async Task<StoredItem?> CreateIfAbsentAsync(string path, byte[] content, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        using var response = await SendAsync(
            HttpMethod.Put,
            ItemByPathUri(path) + ":/content?@microsoft.graph.conflictBehavior=fail",
            content,
            null,
            true,
            cancellationToken).ConfigureAwait(false);
        if (response.StatusCode is HttpStatusCode.Conflict or HttpStatusCode.PreconditionFailed)
        {
            return null;
        }

        response.EnsureSuccessStatusCode();
        return await ParseItemAsync(response, path, content).ConfigureAwait(false);
    }

    public async Task<StoredItem> UpdateIfMatchAsync(string path, byte[] content, string etag, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        using var response = await SendAsync(
            HttpMethod.Put,
            ItemByPathUri(path) + ":/content",
            content,
            etag,
            false,
            cancellationToken).ConfigureAwait(false);
        if (response.StatusCode == HttpStatusCode.PreconditionFailed)
        {
            var current = await GetMetadataAsync(path, cancellationToken).ConfigureAwait(false);
            throw new ETagConflictException(path, etag, current?.ETag ?? "unknown");
        }

        response.EnsureSuccessStatusCode();
        return await ParseItemAsync(response, path, content).ConfigureAwait(false);
    }

    public async Task DeleteAsync(string path, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        using var response = await SendAsync(
            HttpMethod.Delete,
            ItemByPathUri(path),
            null,
            null,
            false,
            cancellationToken).ConfigureAwait(false);
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            return;
        }

        response.EnsureSuccessStatusCode();
    }

    public async Task<DeltaPage> ListDeltaAsync(string? cursor, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        var initialUri = $"{GraphRoot}/drives/{_driveId}/items/{_approotId}/delta";
        var next = string.IsNullOrWhiteSpace(cursor) ? initialUri : cursor;
        var items = new List<StoredItem>();
        var deletions = new List<DeletedItem>();
        var reset = false;
        string? finalCursor = null;
        while (!string.IsNullOrWhiteSpace(next))
        {
            using var response = await SendAsync(HttpMethod.Get, next, null, null, false, cancellationToken).ConfigureAwait(false);
            if (response.StatusCode == HttpStatusCode.Gone && !reset)
            {
                reset = true;
                next = initialUri;
                items.Clear();
                deletions.Clear();
                continue;
            }

            response.EnsureSuccessStatusCode();
            using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
            if (doc.RootElement.TryGetProperty("value", out var value))
            {
                foreach (var el in value.EnumerateArray())
                {
                    var id = el.TryGetProperty("id", out var idElement)
                        ? idElement.GetString() ?? ""
                        : "";
                    var path = DeltaPath(el);
                    if (el.TryGetProperty("deleted", out _) || el.TryGetProperty("@removed", out _))
                    {
                        deletions.Add(new DeletedItem(id, path));
                        continue;
                    }
                    if (!el.TryGetProperty("file", out _) || path is null)
                    {
                        continue;
                    }

                    items.Add(new StoredItem(
                        path,
                        ReadEtag(el),
                        id.Length > 0 ? id : path,
                        ReadTimestamp(el),
                        Content: null));
                }
            }

            finalCursor = doc.RootElement.TryGetProperty("@odata.deltaLink", out var delta)
                ? delta.GetString()
                : finalCursor;
            next = doc.RootElement.TryGetProperty("@odata.nextLink", out var nextLink)
                ? nextLink.GetString()
                : null;
        }

        return new DeltaPage(items, finalCursor)
        {
            Deletions = deletions,
            Reset = reset,
        };
    }

    public async Task<ArtifactResolution?> ResolveByItemIdAsync(string driveItemId, CancellationToken cancellationToken = default)
    {
        await EnsureApprootAsync(cancellationToken).ConfigureAwait(false);
        using var response = await SendAsync(
            HttpMethod.Get,
            $"{GraphRoot}/drives/{_driveId}/items/{Uri.EscapeDataString(driveItemId)}",
            null,
            null,
            false,
            cancellationToken).ConfigureAwait(false);
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }

        response.EnsureSuccessStatusCode();
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
        var el = doc.RootElement;
        return new ArtifactResolution(
            DriveItemId: el.GetProperty("id").GetString() ?? driveItemId,
            Name: el.TryGetProperty("name", out var name) ? name.GetString() ?? driveItemId : driveItemId,
            SizeBytes: el.TryGetProperty("size", out var size) ? size.GetInt64() : null,
            CreatedUtc: ReadTimestamp(el),
            WebUrl: el.TryGetProperty("webUrl", out var url) ? url.GetString() : null);
    }

    private async Task EnsureApprootAsync(CancellationToken cancellationToken)
    {
        if (_approotId is not null && _driveId is not null)
        {
            return;
        }

        using var response = await SendAsync(
            HttpMethod.Get,
            $"{GraphRoot}/me/drive/special/approot",
            null,
            null,
            false,
            cancellationToken).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
        _approotId = doc.RootElement.GetProperty("id").GetString();
        var approotName = doc.RootElement.TryGetProperty("name", out var name)
            ? name.GetString()
            : null;
        _driveId = doc.RootElement.TryGetProperty("parentReference", out var parent)
            && parent.TryGetProperty("driveId", out var drive)
            ? drive.GetString()
            : doc.RootElement.TryGetProperty("id", out _)
                ? null
                : null;
        if (approotName is not null
            && doc.RootElement.TryGetProperty("parentReference", out var parentReference)
            && parentReference.TryGetProperty("path", out var parentPath))
        {
            _approotPath = GraphRelative(parentPath.GetString() ?? "").TrimEnd('/') + "/" + approotName;
        }
        if (_driveId is null && doc.RootElement.TryGetProperty("parentReference", out var pr)
            && pr.TryGetProperty("driveId", out var d))
        {
            _driveId = d.GetString();
        }

        if (_approotId is null)
        {
            throw new InvalidOperationException("Graph approot id was missing.");
        }

        if (_driveId is null)
        {
            using var driveResponse = await SendAsync(
                HttpMethod.Get,
                $"{GraphRoot}/me/drive",
                null,
                null,
                false,
                cancellationToken).ConfigureAwait(false);
            driveResponse.EnsureSuccessStatusCode();
            using var driveDoc = JsonDocument.Parse(await driveResponse.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
            _driveId = driveDoc.RootElement.GetProperty("id").GetString();
        }
    }

    private string ItemByPathUri(string path)
    {
        var rel = Normalize(path);
        var escaped = string.Join('/', rel.Split('/').Select(Uri.EscapeDataString));
        return $"{GraphRoot}/drives/{_driveId}/items/{_approotId}:/{escaped}";
    }

    private async Task<StoredItem?> GetMetadataAsync(string path, CancellationToken cancellationToken)
    {
        using var response = await SendAsync(
            HttpMethod.Get,
            ItemByPathUri(path),
            null,
            null,
            false,
            cancellationToken).ConfigureAwait(false);
        if (response.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }

        response.EnsureSuccessStatusCode();
        return await ParseItemAsync(response, path, content: null).ConfigureAwait(false);
    }

    private async IAsyncEnumerable<StoredItem> EnumerateChildrenAsync(
        string uri,
        string folder,
        [System.Runtime.CompilerServices.EnumeratorCancellation] CancellationToken cancellationToken)
    {
        var next = uri;
        while (!string.IsNullOrEmpty(next))
        {
            using var response = await SendAsync(HttpMethod.Get, next, null, null, false, cancellationToken).ConfigureAwait(false);
            response.EnsureSuccessStatusCode();
            using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
            if (doc.RootElement.TryGetProperty("value", out var value))
            {
                foreach (var el in value.EnumerateArray())
                {
                    var name = el.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "";
                    var childPath = string.IsNullOrEmpty(folder) ? name : $"{folder.TrimEnd('/')}/{name}";
                    if (el.TryGetProperty("folder", out _))
                    {
                        var childUri = ItemByPathUri(childPath) + ":/children";
                        await foreach (var nested in EnumerateChildrenAsync(childUri, childPath, cancellationToken).ConfigureAwait(false))
                        {
                            yield return nested;
                        }

                        continue;
                    }

                    yield return new StoredItem(
                        Path: childPath,
                        ETag: ReadEtag(el),
                        ItemId: el.TryGetProperty("id", out var id) ? id.GetString() ?? childPath : childPath,
                        LastModifiedUtc: ReadTimestamp(el),
                        Content: null);
                }
            }

            next = doc.RootElement.TryGetProperty("@odata.nextLink", out var link) ? link.GetString() : null;
        }
    }

    private async Task<HttpResponseMessage> SendAsync(
        HttpMethod method,
        string uri,
        byte[]? content,
        string? ifMatch,
        bool ifNoneMatch,
        CancellationToken cancellationToken)
    {
        var target = new Uri(uri);
        var graphOrigin = new Uri(GraphRoot);
        if (!string.Equals(target.Scheme, graphOrigin.Scheme, StringComparison.OrdinalIgnoreCase)
            || !string.Equals(target.Host, graphOrigin.Host, StringComparison.OrdinalIgnoreCase)
            || target.Port != graphOrigin.Port)
        {
            throw new InvalidOperationException("Refusing to send a Graph token to an unexpected origin.");
        }

        for (var attempt = 0; ; attempt++)
        {
            var token = await _identity.AcquireGraphTokenAsync(cancellationToken).ConfigureAwait(false);
            using var request = new HttpRequestMessage(method, uri);
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            if (!string.IsNullOrEmpty(ifMatch))
            {
                request.Headers.TryAddWithoutValidation("If-Match", QuoteEtag(ifMatch));
            }

            if (ifNoneMatch)
            {
                request.Headers.TryAddWithoutValidation("If-None-Match", "*");
            }

            if (content is not null)
            {
                request.Content = new ByteArrayContent(content);
            }

            var response = await _http.SendAsync(request, cancellationToken).ConfigureAwait(false);
            if (!IsRetryable(response.StatusCode) || attempt >= _maxRetries)
            {
                return response;
            }

            var delay = TimeSpan.FromSeconds(Math.Pow(2, attempt));
            if (response.Headers.RetryAfter?.Delta is { } headerDelay)
            {
                delay = headerDelay;
            }
            else if (response.Headers.RetryAfter?.Date is { } date)
            {
                delay = date - _clock();
                if (delay < TimeSpan.Zero)
                {
                    delay = TimeSpan.FromSeconds(1);
                }
            }

            delay = delay < TimeSpan.FromSeconds(1)
                ? TimeSpan.FromSeconds(1)
                : delay > TimeSpan.FromSeconds(30)
                    ? TimeSpan.FromSeconds(30)
                    : delay;

            response.Dispose();
            await _delay(delay, cancellationToken).ConfigureAwait(false);
        }
    }

    private static async Task<StoredItem> ParseItemAsync(HttpResponseMessage response, string path, byte[]? content)
    {
        var body = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
        if (string.IsNullOrWhiteSpace(body))
        {
            return new StoredItem(Normalize(path), ETagFallback(content ?? []), Normalize(path), DateTimeOffset.UtcNow, content);
        }

        using var doc = JsonDocument.Parse(body);
        var el = doc.RootElement;
        return new StoredItem(
            Path: Normalize(path),
            ETag: ReadEtag(el),
            ItemId: el.TryGetProperty("id", out var id) ? id.GetString() ?? Normalize(path) : Normalize(path),
            LastModifiedUtc: ReadTimestamp(el),
            Content: content);
    }

    private static string ReadEtag(JsonElement el)
    {
        if (el.TryGetProperty("eTag", out var etag) && etag.GetString() is { Length: > 0 } value)
        {
            return value.Trim('"');
        }

        if (el.TryGetProperty("cTag", out var ctag) && ctag.GetString() is { Length: > 0 } cvalue)
        {
            return cvalue.Trim('"');
        }

        return "unknown";
    }

    private static DateTimeOffset ReadTimestamp(JsonElement el)
    {
        if (el.TryGetProperty("lastModifiedDateTime", out var ts) && DateTimeOffset.TryParse(ts.GetString(), out var parsed))
        {
            return parsed;
        }

        if (el.TryGetProperty("createdDateTime", out var created) && DateTimeOffset.TryParse(created.GetString(), out var createdParsed))
        {
            return createdParsed;
        }

        return DateTimeOffset.UtcNow;
    }

    private static bool IsRetryable(HttpStatusCode status) =>
        status is HttpStatusCode.TooManyRequests or HttpStatusCode.ServiceUnavailable;

    private string? DeltaPath(JsonElement item)
    {
        var name = item.TryGetProperty("name", out var nameElement)
            ? nameElement.GetString()
            : null;
        if (string.IsNullOrWhiteSpace(name))
        {
            return null;
        }

        if (!item.TryGetProperty("parentReference", out var parent)
            || !parent.TryGetProperty("path", out var pathElement))
        {
            return Normalize(name);
        }

        var fullPath = (GraphRelative(pathElement.GetString() ?? "").TrimEnd('/') + "/" + name).TrimStart('/');
        var root = _approotPath?.Trim('/');
        if (!string.IsNullOrWhiteSpace(root)
            && (string.Equals(fullPath, root, StringComparison.Ordinal)
                || fullPath.StartsWith(root + "/", StringComparison.Ordinal)))
        {
            return fullPath[root.Length..].TrimStart('/');
        }

        foreach (var protocolRoot in new[] { "agents", "commands", "responses", "conversations", "activity", "telemetry" })
        {
            var marker = "/" + protocolRoot + "/";
            var index = ("/" + fullPath).IndexOf(marker, StringComparison.Ordinal);
            if (index >= 0)
            {
                return ("/" + fullPath)[(index + 1)..];
            }
        }

        return Normalize(name);
    }

    private static string GraphRelative(string path) =>
        path.Contains("root:", StringComparison.Ordinal)
            ? path[(path.IndexOf("root:", StringComparison.Ordinal) + "root:".Length)..].Trim('/')
            : path.Trim('/');

    private static string QuoteEtag(string etag) =>
        etag.StartsWith('"') ? etag : $"\"{etag}\"";

    private static string Normalize(string path)
    {
        ArgumentNullException.ThrowIfNull(path);
        var normalized = path.Replace('\\', '/').TrimStart('/');
        var segments = normalized.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (segments.Any(segment => segment is "." or ".." || segment.Contains('\0')))
        {
            throw new ArgumentException($"Invalid App Folder path: {path}", nameof(path));
        }

        return string.Join('/', segments);
    }

    private static string ETagFallback(byte[] content) =>
        Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(content)).ToLowerInvariant();
}

public sealed record ArtifactResolution(
    string DriveItemId,
    string Name,
    long? SizeBytes,
    DateTimeOffset? CreatedUtc,
    string? WebUrl);
