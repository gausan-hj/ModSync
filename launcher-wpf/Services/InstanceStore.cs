using System.IO;
using System.Text.Json;

namespace ModSyncLauncher.Services;

public sealed class InstanceProfile
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Name { get; set; } = "新实例";
    public string VersionId { get; set; } = "26.3";
    public string Loader { get; set; } = "neoforge";
    public string? LoaderVersionId { get; set; }
    public string GameDir { get; set; } = "";
    public bool ImportGlobalMods { get; set; } = true;
    public string ModsDir => Path.Combine(GameDir, "mods");

    public override string ToString() => Name;
}

public static class InstanceStore
{
    private static readonly string Root = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "ModSyncLauncher");
    private static readonly string FilePath = Path.Combine(Root, "instances.json");

    public static List<InstanceProfile> Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                return JsonSerializer.Deserialize<List<InstanceProfile>>(File.ReadAllText(FilePath)) ?? new();
            }
        }
        catch
        {
            // Fall through to defaults.
        }
        return new List<InstanceProfile>();
    }

    public static void Save(List<InstanceProfile> instances)
    {
        Directory.CreateDirectory(Root);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(instances, new JsonSerializerOptions { WriteIndented = true }));
    }

    public static InstanceProfile Create(string name, string version, string? gameDir = null, bool importGlobal = true, string loader = "neoforge")
    {
        var id = Guid.NewGuid().ToString("N");
        var dir = gameDir ?? Path.Combine(Root, "instances", id);
        Directory.CreateDirectory(Path.Combine(dir, "mods"));
        Directory.CreateDirectory(Path.Combine(dir, "config"));
        return new InstanceProfile
        {
            Id = id,
            Name = name,
            VersionId = version,
            Loader = loader,
            GameDir = dir,
            ImportGlobalMods = importGlobal,
        };
    }
}
