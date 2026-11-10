using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ModSyncLauncher.Services;

public sealed class MinecraftVersionEntry
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("type")] public string Type { get; set; } = "";
    [JsonPropertyName("url")] public string Url { get; set; } = "";

    public override string ToString() => Id;
}

public sealed class MinecraftVersionManifest
{
    [JsonPropertyName("versions")] public List<MinecraftVersionEntry> Versions { get; set; } = new();
}

public static class VersionDownloader
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(2) };
    private const string ManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static MinecraftVersionManifest? _cached;

    static VersionDownloader()
    {
        Http.DefaultRequestHeaders.UserAgent.ParseAdd("ModSync-Launcher/0.1");
    }

    public static async Task<List<MinecraftVersionEntry>> GetVersionsAsync(bool releasesOnly = true, CancellationToken ct = default)
    {
        if (_cached == null)
        {
            var json = await Http.GetStringAsync(ManifestUrl, ct);
            _cached = JsonSerializer.Deserialize<MinecraftVersionManifest>(json) ?? new();
        }
        return _cached.Versions
            .Where(v => !releasesOnly || string.Equals(v.Type, "release", StringComparison.OrdinalIgnoreCase))
            .ToList();
    }

    public static async Task DownloadAsync(string versionId, string minecraftDir, Action<string> log, CancellationToken ct = default)
    {
        var versions = await GetVersionsAsync(false, ct);
        var entry = versions.FirstOrDefault(v => v.Id == versionId)
                    ?? throw new InvalidOperationException("找不到这个版本");
        log($"读取 {versionId} 版本信息…");
        var json = await Http.GetStringAsync(entry.Url, ct);
        using var doc = JsonDocument.Parse(json);
        var versionDir = Path.Combine(minecraftDir, "versions", versionId);
        Directory.CreateDirectory(versionDir);
        await File.WriteAllTextAsync(Path.Combine(versionDir, $"{versionId}.json"), json, ct);

        if (doc.RootElement.TryGetProperty("downloads", out var downloads) &&
            downloads.TryGetProperty("client", out var client) &&
            client.TryGetProperty("url", out var clientUrl))
        {
            var target = Path.Combine(versionDir, $"{versionId}.jar");
            if (!File.Exists(target) || new FileInfo(target).Length == 0)
            {
                log($"下载 {versionId} 客户端…");
                var bytes = await Http.GetByteArrayAsync(clientUrl.GetString(), ct);
                await File.WriteAllBytesAsync(target, bytes, ct);
            }
        }
        log($"{versionId} 已加入版本列表");
    }
}
