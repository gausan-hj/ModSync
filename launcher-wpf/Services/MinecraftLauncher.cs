using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using ModSyncLauncher.Services;

namespace ModSyncLauncher.Services;

public sealed record LaunchRequest(
    string VersionId,
    string GameDir,
    string SharedDir,
    AccountProfile Account,
    int MemoryMb,
    string? JavaPath,
    Action<string> Log);

/// <summary>
/// Minimal offline Minecraft launcher: version JSON inheritance, libraries,
/// natives, assets, logging config and the Java process.
/// </summary>
public static class MinecraftLauncher
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(10) };
    private static Process? _process;

    public static string DefaultGameDir => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");

    public static IReadOnlyList<string> ListVersions(string gameDir)
    {
        var root = Path.Combine(gameDir, "versions");
        if (!Directory.Exists(root)) return Array.Empty<string>();
        return Directory.GetDirectories(root)
            .Select(Path.GetFileName)
            .Where(name => !string.IsNullOrWhiteSpace(name) &&
                           File.Exists(Path.Combine(root, name!, name + ".json")))
            .OrderByDescending(name => name, StringComparer.OrdinalIgnoreCase)
            .ToList()!;
    }

    public static async Task<Process> LaunchAsync(LaunchRequest request, CancellationToken ct = default)
    {
        var log = request.Log;
        log($"读取版本 {request.VersionId}…");
        var version = await ResolveVersionAsync(request.SharedDir, request.VersionId, ct);
        var baseId = version["_baseId"]?.GetValue<string>() ?? request.VersionId;
        var versionDir = Path.Combine(request.SharedDir, "versions", request.VersionId);
        Directory.CreateDirectory(versionDir);

        // client jar (usually inherited from the base version)
        var clientJar = Path.Combine(versionDir, $"{baseId}.jar");
        if (!File.Exists(clientJar))
        {
            clientJar = Path.Combine(request.SharedDir, "versions", baseId, $"{baseId}.jar");
        }
        var clientDownload = version["downloads"]?["client"];
        if (clientDownload != null)
        {
            await EnsureFileAsync(clientJar, clientDownload["url"]?.GetValue<string>(),
                clientDownload["sha1"]?.GetValue<string>(), log, ct);
        }
        if (!File.Exists(clientJar))
        {
            throw new FileNotFoundException($"找不到客户端 jar：{clientJar}");
        }

        var classpath = new List<string>();
        var nativeJars = new List<string>();
        log("检查依赖库…");
        foreach (var node in version["libraries"]?.AsArray() ?? new JsonArray())
        {
            if (node is not JsonObject library || !RulesAllow(library["rules"])) continue;
            var artifact = library["downloads"]?["artifact"];
            if (artifact != null)
            {
                var relative = artifact["path"]?.GetValue<string>();
                if (!string.IsNullOrWhiteSpace(relative))
                {
                    var target = Path.Combine(request.SharedDir, "libraries", relative.Replace('/', Path.DirectorySeparatorChar));
                    await EnsureFileAsync(target, artifact["url"]?.GetValue<string>(),
                        artifact["sha1"]?.GetValue<string>(), log, ct, quiet: true);
                    if (File.Exists(target))
                    {
                        var libraryName = library["name"]?.GetValue<string>() ?? "";
                        if (libraryName.Contains("natives-windows", StringComparison.OrdinalIgnoreCase) ||
                            relative.Contains("natives-windows", StringComparison.OrdinalIgnoreCase))
                        {
                            nativeJars.Add(target);
                        }
                        else
                        {
                            classpath.Add(target);
                        }
                    }
                }
            }

            var nativeKey = library["natives"]?["windows"]?.GetValue<string>();
            if (!string.IsNullOrWhiteSpace(nativeKey))
            {
                var classifier = library["downloads"]?["classifiers"]?[nativeKey];
                var relative = classifier?["path"]?.GetValue<string>();
                if (!string.IsNullOrWhiteSpace(relative))
                {
                    var target = Path.Combine(request.SharedDir, "libraries", relative.Replace('/', Path.DirectorySeparatorChar));
                    await EnsureFileAsync(target, classifier?["url"]?.GetValue<string>(),
                        classifier?["sha1"]?.GetValue<string>(), log, ct, quiet: true);
                    if (File.Exists(target)) nativeJars.Add(target);
                }
            }
        }
        classpath.Add(clientJar);
        classpath = classpath.Distinct(StringComparer.OrdinalIgnoreCase).ToList();

        var nativesDir = Path.Combine(versionDir, "natives");
        var javaPathDir = Path.Combine(nativesDir, "java");
        Directory.CreateDirectory(nativesDir);
        Directory.CreateDirectory(javaPathDir);
        foreach (var nativeJar in nativeJars)
        {
            ExtractNatives(nativeJar, javaPathDir);
        }

        log("检查资源文件…");
        var assetsRoot = Path.Combine(request.SharedDir, "assets");
        var assetIndexId = await EnsureAssetsAsync(version, assetsRoot, log, ct);

        var loggingArg = await EnsureLoggingAsync(version, assetsRoot, log, ct);
        var java = !string.IsNullOrWhiteSpace(request.JavaPath)
            ? request.JavaPath!
            : FindJava(version["javaVersion"]?["majorVersion"]?.GetValue<int>() ?? 21, log);

        var replacements = new Dictionary<string, string>(StringComparer.Ordinal)
        {
            ["auth_player_name"] = request.Account.Name,
            ["version_name"] = request.VersionId,
            ["game_directory"] = request.GameDir,
            ["assets_root"] = assetsRoot,
            ["assets_index_name"] = assetIndexId,
            ["auth_uuid"] = string.IsNullOrWhiteSpace(request.Account.Uuid) ? OfflineUuid(request.Account.Name) : request.Account.Uuid,
            ["auth_access_token"] = string.IsNullOrWhiteSpace(request.Account.AccessToken) ? "0" : request.Account.AccessToken,
            ["clientid"] = "",
            ["auth_xuid"] = "",
            ["user_type"] = string.IsNullOrWhiteSpace(request.Account.AccessToken) ? "legacy" : "msa",
            ["version_type"] = "release",
            ["natives_directory"] = nativesDir,
            ["launcher_name"] = "ModSync",
            ["launcher_version"] = "1.4.0",
            ["classpath"] = string.Join(Path.PathSeparator, classpath),
            ["resolution_width"] = "1280",
            ["resolution_height"] = "720",
            ["quickPlayPath"] = "",
            ["quickPlaySingleplayer"] = "",
            ["quickPlayMultiplayer"] = "",
            ["quickPlayRealms"] = "",
        };

        var args = new List<string>
        {
            $"-Xmx{Math.Max(512, request.MemoryMb)}M",
            $"-Xms{Math.Max(256, request.MemoryMb / 2)}M",
            "-Dfile.encoding=UTF-8",
            $"-Djava.library.path={javaPathDir}",
            $"-Djna.tmpdir={Path.Combine(nativesDir, "jna")}",
            $"-Dorg.lwjgl.system.SharedLibraryExtractPath={Path.Combine(nativesDir, "lwjgl")}",
            $"-Dio.netty.native.workdir={Path.Combine(nativesDir, "netty")}",
            "-Dminecraft.launcher.brand=ModSync",
            "-Dminecraft.launcher.version=1.4.0",
        };
        foreach (var dir in new[] { "jna", "lwjgl", "netty" })
        {
            Directory.CreateDirectory(Path.Combine(nativesDir, dir));
        }

        AddArguments(args, version["arguments"]?["jvm"], replacements, true);
        if (!string.IsNullOrWhiteSpace(loggingArg)) args.Add(loggingArg);
        args.Add("-cp");
        args.Add(string.Join(Path.PathSeparator, classpath));

        var gameArgs = new List<string>();
        AddArguments(gameArgs, version["arguments"]?["game"], replacements, false);
        if (gameArgs.Count == 0 && version["minecraftArguments"] is JsonValue legacy)
        {
            gameArgs.AddRange(SplitLegacyArguments(legacy.GetValue<string>(), replacements));
        }

        var mainClass = version["mainClass"]?.GetValue<string>();
        if (string.IsNullOrWhiteSpace(mainClass)) throw new InvalidDataException("版本 JSON 缺少 mainClass");
        args.Add(mainClass);
        args.AddRange(gameArgs);

        log($"Java: {java}");
        log($"启动参数长度: {args.Sum(a => a.Length)} 字符");
        var javaExe = java;
        if (OperatingSystem.IsWindows())
        {
            var javaw = Path.Combine(Path.GetDirectoryName(java)!, "javaw.exe");
            if (File.Exists(javaw)) javaExe = javaw;
        }
        var startInfo = new ProcessStartInfo
        {
            FileName = javaExe,
            WorkingDirectory = request.GameDir,
            UseShellExecute = false,
            CreateNoWindow = true,
            WindowStyle = ProcessWindowStyle.Hidden,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        foreach (var arg in args) startInfo.ArgumentList.Add(arg);

        var process = new Process { StartInfo = startInfo, EnableRaisingEvents = true };
        process.OutputDataReceived += (_, e) => { if (e.Data != null) log(e.Data); };
        process.ErrorDataReceived += (_, e) => { if (e.Data != null) log(e.Data); };
        process.Exited += (_, _) =>
        {
            try { log($"Minecraft 已退出，代码 {process.ExitCode}"); } catch { }
        };
        process.Start();
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();
        _process = process;
        log($"Minecraft 已启动，PID {process.Id}");
        return process;
    }

    private static async Task<JsonObject> ResolveVersionAsync(string gameDir, string id, CancellationToken ct)
    {
        var jsonPath = Path.Combine(gameDir, "versions", id, id + ".json");
        if (!File.Exists(jsonPath)) throw new FileNotFoundException($"找不到版本 JSON：{jsonPath}");
        var node = JsonNode.Parse(await File.ReadAllTextAsync(jsonPath, ct)) as JsonObject
                   ?? throw new InvalidDataException("版本 JSON 格式错误");
        var parentId = node["inheritsFrom"]?.GetValue<string>();
        if (string.IsNullOrWhiteSpace(parentId))
        {
            node["_baseId"] = id;
            return node;
        }

        var parent = await ResolveVersionAsync(gameDir, parentId, ct);
        foreach (var property in node)
        {
            if (property.Key == "libraries") continue;
            if (property.Key == "arguments" && property.Value is JsonObject childArgs &&
                parent["arguments"] is JsonObject parentArgs)
            {
                var merged = (JsonObject)parentArgs.DeepClone();
                foreach (var section in childArgs)
                {
                    var list = (JsonArray)(merged[section.Key]?.DeepClone() ?? new JsonArray());
                    foreach (var arg in section.Value?.AsArray() ?? new JsonArray())
                    {
                        list.Add(arg?.DeepClone());
                    }
                    merged[section.Key] = list;
                }
                parent["arguments"] = merged;
                continue;
            }
            parent[property.Key] = property.Value?.DeepClone();
        }

        var parentLibraries = parent["libraries"]?.AsArray() ?? new JsonArray();
        foreach (var item in node["libraries"]?.AsArray() ?? new JsonArray())
        {
            parentLibraries.Add(item?.DeepClone());
        }
        parent["libraries"] = parentLibraries;
        return parent;
    }

    private static bool RulesAllow(JsonNode? rulesNode)
    {
        if (rulesNode is not JsonArray rules || rules.Count == 0) return true;
        var allowed = false;
        foreach (var node in rules)
        {
            if (node is not JsonObject rule) continue;
            var matches = true;
            if (rule["os"] is JsonObject os)
            {
                var name = os["name"]?.GetValue<string>();
                if (!string.IsNullOrWhiteSpace(name) && name != "windows") matches = false;
                var arch = os["arch"]?.GetValue<string>();
                if (!string.IsNullOrWhiteSpace(arch) && arch != "x86_64" && arch != "x64") matches = false;
            }
            if (rule["features"] is JsonObject features)
            {
                foreach (var feature in features)
                {
                    if (feature.Value?.GetValue<bool>() == true) matches = false;
                }
            }
            if (!matches) continue;
            allowed = rule["action"]?.GetValue<string>() == "allow";
        }
        return allowed;
    }

    private static void AddArguments(List<string> target, JsonNode? argumentsNode,
        Dictionary<string, string> replacements, bool jvm)
    {
        if (argumentsNode is not JsonArray arguments) return;
        foreach (var entry in arguments)
        {
            if (entry is JsonValue value && value.TryGetValue<string>(out var text))
            {
                target.Add(Substitute(text, replacements));
                continue;
            }
            if (entry is not JsonObject obj || !RulesAllow(obj["rules"])) continue;
            var values = obj["value"];
            if (values is JsonValue single && single.TryGetValue<string>(out var one))
            {
                target.Add(Substitute(one, replacements));
            }
            else if (values is JsonArray array)
            {
                foreach (var item in array)
                {
                    if (item is JsonValue v && v.TryGetValue<string>(out var argument))
                    {
                        target.Add(Substitute(argument, replacements));
                    }
                }
            }
        }
    }

    private static IEnumerable<string> SplitLegacyArguments(string text, Dictionary<string, string> replacements)
    {
        foreach (var part in text.Split(' ', StringSplitOptions.RemoveEmptyEntries))
        {
            yield return Substitute(part, replacements);
        }
    }

    private static string Substitute(string value, Dictionary<string, string> replacements)
    {
        foreach (var pair in replacements)
        {
            value = value.Replace("${" + pair.Key + "}", pair.Value, StringComparison.Ordinal);
        }
        return value;
    }

    private static string OfflineUuid(string name)
    {
        var bytes = MD5.HashData(Encoding.UTF8.GetBytes("OfflinePlayer:" + name));
        bytes[6] = (byte)((bytes[6] & 0x0F) | 0x30);
        bytes[8] = (byte)((bytes[8] & 0x3F) | 0x80);
        return new Guid(bytes).ToString();
    }

    private static async Task<string> EnsureAssetsAsync(JsonObject version, string assetsRoot,
        Action<string> log, CancellationToken ct)
    {
        var index = version["assetIndex"];
        var id = index?["id"]?.GetValue<string>() ?? version["assets"]?.GetValue<string>() ?? "legacy";
        var indexDir = Path.Combine(assetsRoot, "indexes");
        Directory.CreateDirectory(indexDir);
        var indexPath = Path.Combine(indexDir, id + ".json");
        var url = index?["url"]?.GetValue<string>();
        if (!File.Exists(indexPath) && !string.IsNullOrWhiteSpace(url))
        {
            await EnsureFileAsync(indexPath, url, index?["sha1"]?.GetValue<string>(), log, ct);
        }
        if (!File.Exists(indexPath)) return id;

        var objectsDir = Path.Combine(assetsRoot, "objects");
        var indexJson = JsonNode.Parse(await File.ReadAllTextAsync(indexPath, ct)) as JsonObject;
        var objects = indexJson?["objects"] as JsonObject;
        if (objects == null) return id;
        var missing = 0;
        foreach (var item in objects)
        {
            var hash = item.Value?["hash"]?.GetValue<string>();
            if (string.IsNullOrWhiteSpace(hash) || hash.Length < 2) continue;
            var target = Path.Combine(objectsDir, hash[..2], hash);
            if (File.Exists(target)) continue;
            missing++;
            var objectUrl = $"https://resources.download.minecraft.net/{hash[..2]}/{hash}";
            await EnsureFileAsync(target, objectUrl, null, log, ct, quiet: true);
        }
        if (missing > 0) log($"资源文件补齐 {missing} 个");
        return id;
    }

    private static async Task<string?> EnsureLoggingAsync(JsonObject version, string assetsRoot,
        Action<string> log, CancellationToken ct)
    {
        var file = version["logging"]?["client"]?["file"];
        var url = file?["url"]?.GetValue<string>();
        var id = file?["id"]?.GetValue<string>();
        if (string.IsNullOrWhiteSpace(url) || string.IsNullOrWhiteSpace(id)) return null;
        var dir = Path.Combine(assetsRoot, "log_configs");
        Directory.CreateDirectory(dir);
        var target = Path.Combine(dir, id);
        await EnsureFileAsync(target, url, file?["sha1"]?.GetValue<string>(), log, ct, quiet: true);
        return $"-Dlog4j.configurationFile={target}";
    }

    private static async Task EnsureFileAsync(string path, string? url, string? sha1,
        Action<string> log, CancellationToken ct, bool quiet = false)
    {
        if (File.Exists(path)) return;
        if (string.IsNullOrWhiteSpace(url))
        {
            throw new FileNotFoundException($"缺少文件且没有下载地址：{path}");
        }
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        if (!quiet) log($"下载 {Path.GetFileName(path)}…");
        var bytes = await Http.GetByteArrayAsync(url, ct);
        if (!string.IsNullOrWhiteSpace(sha1))
        {
            var actual = Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();
            if (!actual.Equals(sha1, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException($"文件校验失败：{Path.GetFileName(path)}");
            }
        }
        await File.WriteAllBytesAsync(path, bytes, ct);
    }

    private static void ExtractNatives(string jarPath, string targetDir)
    {
        using var archive = ZipFile.OpenRead(jarPath);
        foreach (var entry in archive.Entries)
        {
            if (string.IsNullOrEmpty(entry.Name)) continue;
            if (entry.FullName.StartsWith("META-INF/", StringComparison.OrdinalIgnoreCase)) continue;
            var target = Path.Combine(targetDir, entry.Name);
            if (File.Exists(target)) continue;
            entry.ExtractToFile(target, true);
        }
    }

    private static string FindJava(int majorVersion, Action<string> log)
    {
        var candidates = new List<string>();
        var home = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(home)) candidates.Add(Path.Combine(home, "bin", "java.exe"));
        foreach (var root in new[]
                 {
                     @"C:\Program Files\Eclipse Adoptium",
                     @"C:\Program Files\Java",
                     @"C:\Program Files\Microsoft",
                 })
        {
            if (!Directory.Exists(root)) continue;
            foreach (var dir in Directory.GetDirectories(root))
            {
                candidates.Add(Path.Combine(dir, "bin", "java.exe"));
            }
        }
        var localJdk = Path.Combine(Environment.CurrentDirectory, ".tools", "jdk25");
        if (Directory.Exists(localJdk))
        {
            foreach (var dir in Directory.GetDirectories(localJdk))
            {
                candidates.Add(Path.Combine(dir, "bin", "java.exe"));
            }
        }
        var valid = candidates.Where(File.Exists).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        var chosen = valid.FirstOrDefault(path =>
        {
            try
            {
                var info = FileVersionInfo.GetVersionInfo(path);
                return info.FileMajorPart == majorVersion;
            }
            catch
            {
                return false;
            }
        });
        chosen ??= valid.FirstOrDefault();
        if (chosen == null) throw new FileNotFoundException($"找不到 Java {majorVersion}");
        log($"使用 Java：{chosen}");
        return chosen;
    }
}
