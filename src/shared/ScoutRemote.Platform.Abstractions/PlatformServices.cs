namespace ScoutRemote.Platform.Abstractions;

/// <summary>
/// OS-protected secret storage. Android: Keystore-backed. iOS (Phase 4): Keychain-backed.
/// MSAL token caches live behind this abstraction and never anywhere else (spec §10, §16.1).
/// </summary>
public interface ISecureStorage
{
    Task<byte[]?> GetAsync(string key, CancellationToken cancellationToken);
    Task SetAsync(string key, byte[] value, CancellationToken cancellationToken);
    Task RemoveAsync(string key, CancellationToken cancellationToken);
}

/// <summary>
/// Background work at OS-permitted intervals. Android: WorkManager-style constraints.
/// Push infrastructure is a later, optional deployment mode (spec §9.2, §17).
/// </summary>
public interface IBackgroundScheduler
{
    Task ScheduleStatusRefreshAsync(TimeSpan interval, CancellationToken cancellationToken);
    Task CancelScheduledWorkAsync(CancellationToken cancellationToken);
}

/// <summary>
/// User notifications. Policy (spec §9.2): notify on command completion/failure/
/// waiting_for_desktop_approval; never per heartbeat — routine activity collapses to a digest.
/// </summary>
public interface INotificationService
{
    Task NotifyCommandOutcomeAsync(string commandId, string title, string body, CancellationToken cancellationToken);
    Task ShowAttentionRequiredAsync(string reason, CancellationToken cancellationToken);
}

/// <summary>Platform auth-callback/browser handling for MSAL redirects.</summary>
public interface IAuthCallbackHandler
{
    Task<Uri> LaunchInteractiveSignInAsync(Uri authorizationUri, Uri redirectUri, CancellationToken cancellationToken);
}

/// <summary>Opens artifacts in the appropriate Microsoft 365 app via OneDrive links (spec §9).</summary>
public interface IExternalAppLauncher
{
    Task<bool> OpenAsync(Uri target, CancellationToken cancellationToken);
}
