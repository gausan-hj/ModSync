using System.IO;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ModSyncLauncher.Services;

public sealed class ModrinthHit
{
    [JsonPropertyName("project_id")] public string ProjectId { get; set; } = "";
    [JsonPropertyName("slug")] public string Slug { get; set; } = "";
    [JsonPropertyName("title")] public string Title { get; set; } = "";
    [JsonPropertyName("description")] public string Description { get; set; } = "";
    [JsonPropertyName("icon_url")] public string? IconUrl { get; set; }
    [JsonPropertyName("downloads")] public long Downloads { get; set; }
    [JsonPropertyName("versions")] public List<string> Versions { get; set; } = new();
}

public sealed class ModrinthSearch
{
    [JsonPropertyName("hits")] public List<ModrinthHit> Hits { get; set; } = new();
}

public sealed class ModrinthVersion
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("project_id")] public string ProjectId { get; set; } = "";
    [JsonPropertyName("version_number")] public string VersionNumber { get; set; } = "";
    [JsonPropertyName("name")] public string Name { get; set; } = "";
    [JsonPropertyName("files")] public List<ModrinthFile> Files { get; set; } = new();
    [JsonPropertyName("dependencies")] public List<ModrinthDependency> Dependencies { get; set; } = new();
}

public sealed class ModrinthFile
{
    [JsonPropertyName("url")] public string Url { get; set; } = "";
    [JsonPropertyName("filename")] public string Filename { get; set; } = "";
    [JsonPropertyName("size")] public long Size { get; set; }
    [JsonPropertyName("primary")] public bool Primary { get; set; }
    [JsonPropertyName("hashes")] public Dictionary<string, string> Hashes { get; set; } = new();
}

public sealed class ModrinthDependency
{
    [JsonPropertyName("version_id")] public string? VersionId { get; set; }
    [JsonPropertyName("project_id")] public string? ProjectId { get; set; }
    [JsonPropertyName("dependency_type")] public string DependencyType { get; set; } = "";
}

public sealed class InstallReport
{
    public List<string> Installed { get; } = new();
    public List<string> AlreadyPresent { get; } = new();
    public List<string> Failed { get; } = new();
}

public static class ModrinthService
{
    private static readonly HttpClient Http = CreateClient();

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromMinutes(3) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("ModSync-Launcher/0.1");
        client.DefaultRequestHeaders.Accept.ParseAdd("application/json");
        return client;
    }

    public static async Task<List<ModrinthHit>> SearchAsync(string query, string loader, string mc, CancellationToken ct = default)
    {
        var facets = new[]
        {
            new[] { "project_type:mod" },
            new[] { $"categories:{loader}" },
            new[] { $"versions:{mc}" },
        };
        var url = "https://api.modrinth.com/v2/search" +
                  $"?query={Uri.EscapeDataString(query ?? "")}" +
                  "&limit=18&index=relevance" +
                  $"&facets={Uri.EscapeDataString(JsonSerializer.Serialize(facets))}";
        var json = await Http.GetStringAsync(url, ct);
        return JsonSerializer.Deserialize<ModrinthSearch>(json)?.Hits ?? new List<ModrinthHit>();
    }

    public static async Task<List<ModrinthVersion>> GetVersionsAsync(string projectId, string loader, string mc,
        CancellationToken ct = default)
    {
        var url = $"https://api.modrinth.com/v2/project/{Uri.EscapeDataString(projectId)}/version" +
                  $"?loaders={Uri.EscapeDataString(JsonSerializer.Serialize(new[] { loader }))}" +
                  $"&game_versions={Uri.EscapeDataString(JsonSerializer.Serialize(new[] { mc }))}";
        var json = await Http.GetStringAsync(url, ct);
        return JsonSerializer.Deserialize<List<ModrinthVersion>>(json) ?? new List<ModrinthVersion>();
    }

    public static async Task<ModrinthVersion?> GetVersionAsync(string versionId, CancellationToken ct = default)
    {
        var json = await Http.GetStringAsync($"https://api.modrinth.com/v2/version/{Uri.EscapeDataString(versionId)}", ct);
        return JsonSerializer.Deserialize<ModrinthVersion>(json);
    }

    public static async Task<InstallReport> InstallAsync(ModrinthHit project, string loader, string mc,
        string modsDir, Action<string> log, CancellationToken ct = default)
    {
        var report = new InstallReport();
        var versions = await GetVersionsAsync(project.ProjectId, loader, mc, ct);
        if (versions.Count == 0)
        {
            throw new InvalidOperationException($"没有找到 {loader} {mc} 的版本");
        }
        Directory.CreateDirectory(modsDir);
        var visited = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        await InstallVersionAsync(versions[0], loader, mc, modsDir, visited, report, log, ct);
        return report;
    }

    private static async Task InstallVersionAsync(ModrinthVersion version, string loader, string mc,
        string modsDir, HashSet<string> visited, InstallReport report, Action<string> log, CancellationToken ct)
    {
        if (!visited.Add(version.Id)) return;
        var file = version.Files.FirstOrDefault(f => f.Primary) ?? version.Files.FirstOrDefault();
        if (file != null && !string.IsNullOrWhiteSpace(file.Url))
        {
            var name = SafeName(file.Filename);
            var target = Path.Combine(modsDir, name);
            if (File.Exists(target) && (file.Size <= 0 || new FileInfo(target).Length == file.Size))
            {
                report.AlreadyPresent.Add(name);
                log($"已存在 {name}");
            }
            else
            {
                log($"下载 {name}…");
                var bytes = await Http.GetByteArrayAsync(file.Url, ct);
                if (file.Hashes.TryGetValue("sha512", out var expected) && !string.IsNullOrWhiteSpace(expected))
                {
                    var actual = Convert.ToHexString(SHA512.HashData(bytes));
                    if (!actual.Equals(expected, StringComparison.OrdinalIgnoreCase))
                    {
                        throw new InvalidDataException($"SHA-512 校验失败：{name}");
                    }
                }
                await File.WriteAllBytesAsync(target, bytes, ct);
                report.Installed.Add(name);
                log($"已安装 {name}");
            }
        }

        foreach (var dependency in version.Dependencies.Where(d => d.DependencyType == "required"))
        {
            try
            {
                ModrinthVersion? depVersion = null;
                if (!string.IsNullOrWhiteSpace(dependency.VersionId))
                {
                    depVersion = await GetVersionAsync(dependency.VersionId, ct);
                }
                else if (!string.IsNullOrWhiteSpace(dependency.ProjectId))
                {
                    var versions = await GetVersionsAsync(dependency.ProjectId, loader, mc, ct);
                    depVersion = versions.FirstOrDefault();
                }
                if (depVersion != null)
                {
                    await InstallVersionAsync(depVersion, loader, mc, modsDir, visited, report, log, ct);
                }
            }
            catch (Exception ex)
            {
                report.Failed.Add(dependency.ProjectId ?? dependency.VersionId ?? "unknown");
                log($"前置安装失败：{ex.Message}");
            }
        }
    }

    private static string SafeName(string name)
    {
        var fileName = Path.GetFileName(name);
        foreach (var c in Path.GetInvalidFileNameChars())
        {
            fileName = fileName.Replace(c, '_');
        }
        return string.IsNullOrWhiteSpace(fileName) ? "mod.jar" : fileName;
    }
}
