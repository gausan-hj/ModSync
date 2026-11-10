using System.IO;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ModSyncLauncher.Services;

public sealed class SyncManifestFile
{
    [JsonPropertyName("file")] public string File { get; set; } = "";
    [JsonPropertyName("size")] public long Size { get; set; }
    [JsonPropertyName("sha256")] public string Sha256 { get; set; } = "";
}

public sealed class SyncManifest
{
    [JsonPropertyName("files")] public List<SyncManifestFile> Files { get; set; } = new();
}

public sealed class SyncReport
{
    public int Installed { get; set; }
    public int Skipped { get; set; }
    public int Failed { get; set; }
}

public static class ModSyncHttpSync
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(10) };

    static ModSyncHttpSync()
    {
        Http.DefaultRequestHeaders.UserAgent.ParseAdd("ModSync-Launcher/0.1");
        Http.DefaultRequestHeaders.Accept.ParseAdd("application/json");
    }

    public static async Task<SyncReport> SyncAsync(string url, string modsDir, Action<string> log,
        CancellationToken ct = default)
    {
        var raw = (url ?? "").Trim();
        if (string.IsNullOrWhiteSpace(raw)) throw new InvalidOperationException("没有填写同步地址");
        string manifestUrl;
        string baseUrl;
        if (raw.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
        {
            manifestUrl = raw;
            baseUrl = raw[..raw.LastIndexOf('/')];
        }
        else
        {
            var trimmed = raw.TrimEnd('/');
            if (!trimmed.Contains("/modsync", StringComparison.OrdinalIgnoreCase)) trimmed += "/modsync";
            baseUrl = trimmed;
            manifestUrl = trimmed + "/manifest.json";
        }

        log("读取 ModSync 模组清单…");
        var json = await Http.GetStringAsync(manifestUrl, ct);
        var manifest = JsonSerializer.Deserialize<SyncManifest>(json) ?? new SyncManifest();
        Directory.CreateDirectory(modsDir);
        var report = new SyncReport();
        foreach (var file in manifest.Files)
        {
            ct.ThrowIfCancellationRequested();
            var name = SafeName(file.File);
            if (string.IsNullOrWhiteSpace(name)) continue;
            var target = Path.Combine(modsDir, name);
            if (File.Exists(target) && file.Size > 0 && new FileInfo(target).Length == file.Size)
            {
                report.Skipped++;
                continue;
            }
            try
            {
                log($"下载 {name}…");
                var downloadUrl = baseUrl + "/files/" + Uri.EscapeDataString(file.File);
                var bytes = await Http.GetByteArrayAsync(downloadUrl, ct);
                if (!string.IsNullOrWhiteSpace(file.Sha256))
                {
                    var actual = Convert.ToHexString(SHA256.HashData(bytes));
                    if (!actual.Equals(file.Sha256, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidDataException($"SHA-256 校验失败：{name}");
                    }
                }
                var temp = target + ".sync";
                await File.WriteAllBytesAsync(temp, bytes, ct);
                File.Move(temp, target, true);
                report.Installed++;
                log($"已同步 {name}");
            }
            catch (Exception ex)
            {
                report.Failed++;
                log($"同步失败 {name}：{ex.Message}");
            }
        }
        return report;
    }

    private static string SafeName(string name)
    {
        var fileName = Path.GetFileName(name);
        foreach (var c in Path.GetInvalidFileNameChars()) fileName = fileName.Replace(c, '_');
        return fileName;
    }
}
