using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Text.Json;
using System.Windows.Media.Imaging;

namespace ModSyncLauncher.Services;

public static class ModIconService
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(20) };

    public static async Task<BitmapImage?> LoadFromUrlAsync(string? url)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;
        try
        {
            var bytes = await Http.GetByteArrayAsync(url);
            return FromBytes(bytes);
        }
        catch
        {
            return null;
        }
    }

    public static BitmapImage? LoadFromJar(string jarPath)
    {
        try
        {
            using var archive = ZipFile.OpenRead(jarPath);
            var path = FindIconPath(archive);
            if (path == null) return null;
            var entry = archive.GetEntry(path);
            if (entry == null) return null;
            using var stream = entry.Open();
            using var memory = new MemoryStream();
            stream.CopyTo(memory);
            return FromBytes(memory.ToArray());
        }
        catch
        {
            return null;
        }
    }

    private static string? FindIconPath(ZipArchive archive)
    {
        var fabric = archive.GetEntry("fabric.mod.json");
        if (fabric != null)
        {
            try
            {
                using var stream = fabric.Open();
                using var doc = JsonDocument.Parse(stream);
                if (doc.RootElement.TryGetProperty("icon", out var icon))
                {
                    if (icon.ValueKind == JsonValueKind.String) return icon.GetString();
                    if (icon.ValueKind == JsonValueKind.Object)
                    {
                        foreach (var property in icon.EnumerateObject())
                        {
                            var value = property.Value.GetString();
                            if (!string.IsNullOrWhiteSpace(value)) return value;
                        }
                    }
                }
            }
            catch
            {
                // Fall through to the Forge metadata.
            }
        }

        foreach (var metadata in new[] { "META-INF/neoforge.mods.toml", "META-INF/mods.toml" })
        {
            var entry = archive.GetEntry(metadata);
            if (entry == null) continue;
            using var reader = new StreamReader(entry.Open());
            var text = reader.ReadToEnd();
            var match = System.Text.RegularExpressions.Regex.Match(text, "logoFile\\s*=\\s*\"([^\"]+)\"");
            if (match.Success) return match.Groups[1].Value;
        }
        return null;
    }

    public static BitmapImage? FromBytes(byte[] bytes)
    {
        try
        {
            using var stream = new MemoryStream(bytes);
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = stream;
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch
        {
            return null;
        }
    }
}
