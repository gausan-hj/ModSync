using System.IO;
using System.Net.Http;
using System.Text.Json;

namespace ModSyncLauncher.Services;

public static class FabricInstaller
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(40) };

    static FabricInstaller()
    {
        Http.DefaultRequestHeaders.UserAgent.ParseAdd("ModSync-Launcher/0.1");
    }

    public static async Task<string> EnsureAsync(string gameVersion, string minecraftDir, Action<string> log,
        CancellationToken ct = default)
    {
        log($"正在准备 Fabric {gameVersion}…");
        var loadersJson = await Http.GetStringAsync(
            $"https://meta.fabricmc.net/v2/versions/loader/{Uri.EscapeDataString(gameVersion)}", ct);
        using var loadersDoc = JsonDocument.Parse(loadersJson);
        var loader = loadersDoc.RootElement.EnumerateArray()
            .FirstOrDefault(x => !x.TryGetProperty("stable", out var stable) || stable.GetBoolean());
        if (loader.ValueKind == JsonValueKind.Undefined)
        {
            throw new InvalidOperationException("没有可用的 Fabric Loader");
        }
        var loaderVersion = loader.GetProperty("version").GetString();
        var profileJson = await Http.GetStringAsync(
            $"https://meta.fabricmc.net/v2/versions/loader/{Uri.EscapeDataString(gameVersion)}/{Uri.EscapeDataString(loaderVersion!)}/profile/json",
            ct);
        using var profileDoc = JsonDocument.Parse(profileJson);
        var id = profileDoc.RootElement.GetProperty("id").GetString()
                 ?? $"fabric-loader-{loaderVersion}-{gameVersion}";
        var versionDir = Path.Combine(minecraftDir, "versions", id);
        Directory.CreateDirectory(versionDir);
        await File.WriteAllTextAsync(Path.Combine(versionDir, $"{id}.json"), profileJson, ct);
        log($"Fabric {loaderVersion} 已就绪");
        return id;
    }
}
