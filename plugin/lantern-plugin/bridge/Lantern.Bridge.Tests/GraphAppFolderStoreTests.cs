using System.Net;
using System.Net.Http.Headers;
using System.Text;
using Lantern.Bridge.Identity;
using Lantern.Bridge.Storage;
using Xunit;

namespace Lantern.Bridge.Tests;

public sealed class GraphAppFolderStoreTests
{
    [Fact]
    public async Task WriteRetriesServiceUnavailableWithAnIntactBody()
    {
        var bodies = new List<string>();
        var delays = new List<TimeSpan>();
        var handler = new SequenceHandler(
            _ => Task.FromResult(Json(HttpStatusCode.OK, ApproootJson)),
            async request =>
            {
                bodies.Add(await ReadBody(request));
                var response = new HttpResponseMessage(HttpStatusCode.ServiceUnavailable);
                response.Headers.RetryAfter = new RetryConditionHeaderValue(TimeSpan.Zero);
                return response;
            },
            async request =>
            {
                bodies.Add(await ReadBody(request));
                return Json(HttpStatusCode.OK, ItemJson);
            });
        var store = CreateStore(handler, (delay, _) =>
        {
            delays.Add(delay);
            return Task.CompletedTask;
        });

        var item = await store.WriteAsync("agents/primary/status.json", "payload"u8.ToArray());

        Assert.Equal("item-1", item.ItemId);
        Assert.Equal(["payload", "payload"], bodies);
        Assert.Single(delays);
    }

    [Fact]
    public async Task CreateIfAbsentSendsIfNoneMatchOnTheRequest()
    {
        string? ifNoneMatch = null;
        var handler = new SequenceHandler(
            _ => Task.FromResult(Json(HttpStatusCode.OK, ApproootJson)),
            request =>
            {
                ifNoneMatch = string.Join(",", request.Headers.GetValues("If-None-Match"));
                return Task.FromResult(Json(HttpStatusCode.Created, ItemJson));
            });
        var store = CreateStore(handler);

        var item = await store.CreateIfAbsentAsync("commands/inbox/command.json", "{}"u8.ToArray());

        Assert.NotNull(item);
        Assert.Equal("*", ifNoneMatch);
        Assert.Equal(2, handler.RequestCount);
    }

    [Fact]
    public async Task DeltaPaginatesNestedPathsAndDeletions()
    {
        var nextLink = "https://graph.microsoft.com/v1.0/delta-page-2";
        var deltaLink = "https://graph.microsoft.com/v1.0/delta-final";
        var handler = new SequenceHandler(
            _ => Task.FromResult(Json(HttpStatusCode.OK, ApproootJson)),
            _ => Task.FromResult(Json(
                HttpStatusCode.OK,
                $$"""
                {
                  "value": [{
                    "file": {},
                    "id": "status-1",
                    "name": "status.json",
                    "eTag": "\"etag-1\"",
                    "lastModifiedDateTime": "2026-08-15T05:00:00Z",
                    "parentReference": {"path": "/drive/root:/Apps/Lantern/agents/primary"}
                  }],
                  "@odata.nextLink": "{{nextLink}}"
                }
                """)),
            _ => Task.FromResult(Json(
                HttpStatusCode.OK,
                $$"""
                {
                  "value": [{
                    "id": "old-response",
                    "name": "old.json",
                    "@removed": {"reason": "deleted"},
                    "parentReference": {"path": "/drive/root:/Apps/Lantern/responses"}
                  }],
                  "@odata.deltaLink": "{{deltaLink}}"
                }
                """)));
        var store = CreateStore(handler);

        var page = await store.ListDeltaAsync(null);

        Assert.Equal("agents/primary/status.json", Assert.Single(page.Items).Path);
        Assert.Equal("responses/old.json", Assert.Single(page.Deletions).Path);
        Assert.Equal(deltaLink, page.Cursor);
        Assert.Equal(3, handler.RequestCount);
    }

    [Fact]
    public async Task StaleDeltaCursorResetsFromInitialLink()
    {
        var deltaLink = "https://graph.microsoft.com/v1.0/delta-new";
        var handler = new SequenceHandler(
            _ => Task.FromResult(Json(HttpStatusCode.OK, ApproootJson)),
            _ => Task.FromResult(new HttpResponseMessage(HttpStatusCode.Gone)),
            _ => Task.FromResult(Json(
                HttpStatusCode.OK,
                $$"""{"value":[],"@odata.deltaLink":"{{deltaLink}}"}""")));
        var store = CreateStore(handler);

        var page = await store.ListDeltaAsync("https://graph.microsoft.com/v1.0/delta-stale");

        Assert.True(page.Reset);
        Assert.Equal(deltaLink, page.Cursor);
        Assert.Equal(3, handler.RequestCount);
    }

    private static GraphAppFolderStore CreateStore(
        HttpMessageHandler handler,
        Func<TimeSpan, CancellationToken, Task>? delay = null) =>
        new(
            new TestIdentity(),
            new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(5) },
            maxRetries: 2,
            delay: delay);

    private static async Task<string> ReadBody(HttpRequestMessage request) =>
        request.Content is null
            ? ""
            : await request.Content.ReadAsStringAsync();

    private static HttpResponseMessage Json(HttpStatusCode status, string body) =>
        new(status)
        {
            Content = new StringContent(body, Encoding.UTF8, "application/json"),
        };

    private const string ApproootJson =
        """{"id":"approot-1","name":"Lantern","parentReference":{"driveId":"drive-1","path":"/drive/root:/Apps"}}""";

    private const string ItemJson =
        """{"id":"item-1","name":"status.json","eTag":"\"etag-1\"","lastModifiedDateTime":"2026-08-15T03:00:00Z"}""";

    private sealed class TestIdentity : IBridgeIdentity
    {
        public Task<string> AcquireGraphTokenAsync(CancellationToken cancellationToken = default) =>
            Task.FromResult("not-a-real-token");

        public Task<BridgeAccount?> GetCurrentAccountAsync(CancellationToken cancellationToken = default) =>
            Task.FromResult<BridgeAccount?>(null);
    }

    private sealed class SequenceHandler(params Func<HttpRequestMessage, Task<HttpResponseMessage>>[] responses)
        : HttpMessageHandler
    {
        private readonly Queue<Func<HttpRequestMessage, Task<HttpResponseMessage>>> _responses = new(responses);
        public int RequestCount { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            Assert.NotEmpty(_responses);
            RequestCount++;
            return _responses.Dequeue()(request);
        }
    }
}
