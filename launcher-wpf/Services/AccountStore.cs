using System.IO;
using System.Text.Json;
using System.Windows.Media.Imaging;

namespace ModSyncLauncher.Services;

public sealed class AccountProfile
{
    public string Name { get; set; } = "Steve";
    public string Type { get; set; } = "离线账号";
    public string? AvatarPath { get; set; }
    public string? Uuid { get; set; }
    public string? AccessToken { get; set; }
    public string? RefreshToken { get; set; }
    public long ExpiresAt { get; set; }
    public string? SkinUrl { get; set; }
}

public static class AccountStore
{
    private static readonly string Dir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "ModSyncLauncher");
    private static readonly string FilePath = Path.Combine(Dir, "account.json");

    public static AccountProfile Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                return JsonSerializer.Deserialize<AccountProfile>(File.ReadAllText(FilePath)) ?? new AccountProfile();
            }
        }
        catch
        {
            // Ignore a broken local profile and fall back to defaults.
        }
        return new AccountProfile();
    }

    public static void Save(AccountProfile profile)
    {
        Directory.CreateDirectory(Dir);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(profile, new JsonSerializerOptions { WriteIndented = true }));
    }

    public static BitmapImage? LoadAvatar(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
        {
            return null;
        }
        try
        {
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.UriSource = new Uri(path, UriKind.Absolute);
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
