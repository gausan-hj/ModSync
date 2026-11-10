using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace ModSyncLauncher.Services;

public sealed record MicrosoftDeviceCode(string DeviceCode, string UserCode, string VerificationUri,
    int ExpiresIn, int Interval, string Message);

public sealed record MicrosoftLoginResult(string Name, string Uuid, string AccessToken, string RefreshToken,
    long ExpiresAt, string? AvatarPath);

public static class MicrosoftAuthService
{
    private const string ClientId = "fe72edc2-3a6f-4280-90e8-e2beb64ce7e1";
    private const string Scope = "XboxLive.signin offline_access";
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(30) };

    static MicrosoftAuthService()
    {
        Http.DefaultRequestHeaders.UserAgent.ParseAdd("ModSync-Launcher/0.1");
    }

    public static async Task<MicrosoftDeviceCode> StartDeviceCodeAsync(CancellationToken ct = default)
    {
        using var response = await Http.PostAsync(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
            new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["client_id"] = ClientId,
                ["tenant"] = "/consumers",
                ["scope"] = Scope,
            }), ct);
        var json = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException(json);
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        return new MicrosoftDeviceCode(
            root.GetProperty("device_code").GetString() ?? "",
            root.GetProperty("user_code").GetString() ?? "",
            root.GetProperty("verification_uri").GetString() ?? "https://www.microsoft.com/link",
            root.TryGetProperty("expires_in", out var exp) ? exp.GetInt32() : 900,
            root.TryGetProperty("interval", out var interval) ? interval.GetInt32() : 5,
            root.TryGetProperty("message", out var message) ? message.GetString() ?? "" : "");
    }

    public static async Task<string> WaitForAccessTokenAsync(MicrosoftDeviceCode code, CancellationToken ct = default)
    {
        var interval = Math.Max(2, code.Interval);
        var deadline = DateTimeOffset.UtcNow.AddSeconds(Math.Max(30, code.ExpiresIn));
        while (DateTimeOffset.UtcNow < deadline)
        {
            ct.ThrowIfCancellationRequested();
            using var response = await Http.PostAsync(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                new FormUrlEncodedContent(new Dictionary<string, string>
                {
                    ["grant_type"] = "urn:ietf:params:oauth:grant-type:device_code",
                    ["client_id"] = ClientId,
                    ["device_code"] = code.DeviceCode,
                    ["scope"] = Scope,
                }), ct);
            var body = await response.Content.ReadAsStringAsync(ct);
            using var doc = JsonDocument.Parse(body);
            var root = doc.RootElement;
            if (root.TryGetProperty("access_token", out var token))
            {
                return token.GetString() ?? throw new InvalidOperationException("empty access token");
            }
            var error = root.TryGetProperty("error", out var err) ? err.GetString() : "";
            if (error == "authorization_pending")
            {
                await Task.Delay(TimeSpan.FromSeconds(interval), ct);
                continue;
            }
            if (error == "slow_down")
            {
                interval += 3;
                await Task.Delay(TimeSpan.FromSeconds(interval), ct);
                continue;
            }
            throw new InvalidOperationException(root.TryGetProperty("error_description", out var desc)
                ? desc.GetString()
                : body);
        }
        throw new TimeoutException("微软登录已超时");
    }

    public static async Task<MicrosoftLoginResult> CompleteAsync(string microsoftAccessToken, CancellationToken ct = default)
    {
        var xbox = await XboxAuthenticateAsync(microsoftAccessToken, ct);
        var xsts = await XstsAuthorizeAsync(xbox, ct);
        var minecraft = await MinecraftLoginAsync(xsts.Token, xsts.Uhs, ct);
        var profile = await GetProfileAsync(minecraft, ct);
        return profile;
    }

    private static async Task<string> XboxAuthenticateAsync(string accessToken, CancellationToken ct)
    {
        var body = JsonSerializer.Serialize(new
        {
            Properties = new
            {
                AuthMethod = "RPS",
                SiteName = "user.auth.xboxlive.com",
                RpsTicket = accessToken.StartsWith("d=", StringComparison.Ordinal) ? accessToken : "d=" + accessToken,
            },
            RelyingParty = "http://auth.xboxlive.com",
            TokenType = "JWT",
        });
        using var content = new StringContent(body, Encoding.UTF8, "application/json");
        using var response = await Http.PostAsync("https://user.auth.xboxlive.com/user/authenticate", content, ct);
        var text = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException("Xbox 登录失败：" + text);
        using var doc = JsonDocument.Parse(text);
        return doc.RootElement.GetProperty("Token").GetString() ?? throw new InvalidOperationException("Xbox token missing");
    }

    private static async Task<(string Token, string Uhs)> XstsAuthorizeAsync(string xboxToken, CancellationToken ct)
    {
        var body = JsonSerializer.Serialize(new
        {
            Properties = new { SandboxId = "RETAIL", UserTokens = new[] { xboxToken } },
            RelyingParty = "rp://api.minecraftservices.com/",
            TokenType = "JWT",
        });
        using var content = new StringContent(body, Encoding.UTF8, "application/json");
        using var response = await Http.PostAsync("https://xsts.auth.xboxlive.com/xsts/authorize", content, ct);
        var text = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException("XSTS 授权失败：" + text);
        using var doc = JsonDocument.Parse(text);
        var root = doc.RootElement;
        var token = root.GetProperty("Token").GetString() ?? "";
        var uhs = root.GetProperty("DisplayClaims").GetProperty("xui")[0].GetProperty("uhs").GetString() ?? "";
        return (token, uhs);
    }

    private static async Task<string> MinecraftLoginAsync(string xstsToken, string uhs, CancellationToken ct)
    {
        var body = JsonSerializer.Serialize(new
        {
            identityToken = $"XBL3.0 x={uhs};{xstsToken}",
        });
        using var content = new StringContent(body, Encoding.UTF8, "application/json");
        using var response = await Http.PostAsync("https://api.minecraftservices.com/authentication/login_with_xbox", content, ct);
        var text = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException("Minecraft 登录失败：" + text);
        using var doc = JsonDocument.Parse(text);
        return doc.RootElement.GetProperty("access_token").GetString() ?? "";
    }

    private static async Task<MicrosoftLoginResult> GetProfileAsync(string accessToken, CancellationToken ct)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "https://api.minecraftservices.com/minecraft/profile");
        request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", accessToken);
        using var response = await Http.SendAsync(request, ct);
        var text = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException("获取 Minecraft 档案失败：" + text);
        using var doc = JsonDocument.Parse(text);
        var root = doc.RootElement;
        var name = root.GetProperty("name").GetString() ?? "Player";
        var uuid = root.GetProperty("id").GetString() ?? "";
        string? skinUrl = null;
        if (root.TryGetProperty("skins", out var skins) && skins.GetArrayLength() > 0)
        {
            skinUrl = skins[0].TryGetProperty("url", out var url) ? url.GetString() : null;
        }
        var avatar = await DownloadAvatarAsync(skinUrl, uuid, ct);
        return new MicrosoftLoginResult(name, uuid, accessToken, "", DateTimeOffset.UtcNow.AddHours(1).ToUnixTimeSeconds(), avatar);
    }

    private static async Task<string?> DownloadAvatarAsync(string? skinUrl, string uuid, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(skinUrl)) return null;
        try
        {
            var bytes = await Http.GetByteArrayAsync(skinUrl, ct);
            using var stream = new MemoryStream(bytes);
            var decoder = BitmapDecoder.Create(stream, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
            var frame = decoder.Frames[0];
            if (frame.PixelWidth < 16 || frame.PixelHeight < 16) return null;
            var crop = new CroppedBitmap(frame, new Int32Rect(8, 8, 8, 8));
            var scaled = new TransformedBitmap(crop, new ScaleTransform(16, 16));
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(scaled));
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ModSyncLauncher");
            Directory.CreateDirectory(dir);
            var path = Path.Combine(dir, "avatar-" + (string.IsNullOrWhiteSpace(uuid) ? "ms" : uuid) + ".png");
            using var output = File.Create(path);
            encoder.Save(output);
            return path;
        }
        catch
        {
            return null;
        }
    }
}
