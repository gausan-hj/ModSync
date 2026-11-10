using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using ModSyncLauncher.Services;

namespace ModSyncLauncher;

public partial class ModernWindow : Window
{
    private DispatcherTimer? _toastTimer;
    private AccountProfile _account;
    private bool _launching;
    private bool _autoLaunchStarted;
    private List<ModrinthHit> _lastHits = new();
    private AppSettings _settings;
    private readonly Dictionary<string, ModrinthHit> _updates = new(StringComparer.OrdinalIgnoreCase);
    private bool _sortAZ;
    private bool _loadingSettings;
    private bool _loadingInstance;
    private string _activeVersion = "26.3";
    private List<InstanceProfile> _instances = new();
    private InstanceProfile? _activeInstance;

    private string ActiveModsDir => _activeInstance?.ModsDir
        ?? Path.Combine(MinecraftLauncher.DefaultGameDir, "versions", _activeVersion, "mods");

    public ModernWindow()
    {
        InitializeComponent();
        _account = AccountStore.Load();
        _settings = AppSettingsStore.Load();
        ApplyAccount();
        if (Environment.GetCommandLineArgs().Any(a => a.StartsWith("--launch")))
        {
            Loaded += async (_, _) =>
            {
                if (_autoLaunchStarted) return;
                _autoLaunchStarted = true;
                await Task.Delay(400);
                var arg = Environment.GetCommandLineArgs().FirstOrDefault(a => a.StartsWith("--launch="));
                var id = arg is null ? "26.3" : arg["--launch=".Length..];
                LoadVersions();
                if (VersionBox.Items.Contains(id)) VersionBox.SelectedItem = id;
                Launch_Click(this, new RoutedEventArgs());
            };
        }
        if (Environment.GetCommandLineArgs().Any(a => a.StartsWith("--install=")))
        {
            Loaded += async (_, _) =>
            {
                if (_autoLaunchStarted) return;
                _autoLaunchStarted = true;
                await Task.Delay(500);
                var arg = Environment.GetCommandLineArgs().First(a => a.StartsWith("--install="));
                SearchBox.Text = arg["--install=".Length..];
                DownloadLoader.SelectedIndex = 0;
                DownloadVersion.SelectedIndex = 0;
                SearchMods_Click(this, new RoutedEventArgs());
                for (var i = 0; i < 30 && _lastHits.Count == 0; i++) await Task.Delay(200);
                if (_lastHits.Count > 0)
                {
                    var loader = (DownloadLoader.SelectedItem as ComboBoxItem)?.Content?.ToString()?.ToLowerInvariant() ?? "neoforge";
                    var mc = (DownloadVersion.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "1.21.1";
                    await InstallProjectAsync(_lastHits[0], new Button(), loader, mc);
                }
            };
        }
        if (Environment.GetCommandLineArgs().Contains("--screenshot"))
        {
            Loaded += async (_, _) =>
            {
                if (_autoLaunchStarted) return;
                await Task.Delay(300);
                var pageArg = Environment.GetCommandLineArgs().FirstOrDefault(a => a.StartsWith("--page="));
                if (pageArg != null)
                {
                    var page = pageArg["--page=".Length..];
                    if (page == "library") NavLibrary.IsChecked = true;
                    else if (page == "mods") NavMods.IsChecked = true;
                    else if (page == "download") NavDownload.IsChecked = true;
                    else if (page == "settings") NavSettings.IsChecked = true;
                    else NavStart.IsChecked = true;
                }
                await Task.Delay(pageArg?.Contains("download") == true ? 5000 : 150);
                SaveScreenshot();
            };
        }
    }

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        LoadVersions();
        LoadInstances();
        GameDirText.Text = MinecraftLauncher.DefaultGameDir;
        _loadingSettings = true;
        MemoryBox.SelectedIndex = _settings.MemoryMb switch
        {
            2048 => 0, 4096 => 1, 6144 => 2, 8192 => 3, 12288 => 4, 16384 => 5, _ => 1,
        };
        JavaBox.Text = string.IsNullOrWhiteSpace(_settings.JavaPath) ? "自动选择" : _settings.JavaPath;
        _loadingSettings = false;
        UpdateHeroSettings();
        EnsureBundledModSync();
    }

    private void Nav_Checked(object sender, RoutedEventArgs e)
    {
        if (!IsLoaded || sender is not RadioButton tab || tab.Tag is not string key) return;
        StartView.Visibility = key == "start" ? Visibility.Visible : Visibility.Collapsed;
        LibraryView.Visibility = key == "library" ? Visibility.Visible : Visibility.Collapsed;
        ModsView.Visibility = key == "mods" ? Visibility.Visible : Visibility.Collapsed;
        DownloadView.Visibility = key == "download" ? Visibility.Visible : Visibility.Collapsed;
        SettingsView.Visibility = key == "settings" ? Visibility.Visible : Visibility.Collapsed;
        if (key == "library") RefreshInstanceCards();
        if (key == "mods") LoadMods();
        if (key == "download")
        {
            SyncDownloadFilters();
            if (DownloadResults.Children.Count == 0) SearchMods_Click(this, new RoutedEventArgs());
            if (McVersionBox.Items.Count == 0) _ = LoadMinecraftVersionsAsync();
        }
        if (key == "settings")
        {
            SettingsAccountName.Text = _account.Name;
            SettingsAccountType.Text = _account.Type == "微软账号" && !string.IsNullOrWhiteSpace(_account.AccessToken)
                ? "微软账号 · 已完成登录"
                : _account.Type;
        }
    }

    private void LoadMods()
    {
        ModsStack.Children.Clear();
        var modsDir = ActiveModsDir;
        var files = Directory.Exists(modsDir)
            ? Directory.GetFiles(modsDir).Where(f => f.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || f.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase)).ToList()
            : new List<string>();
        if (_sortAZ) files = files.OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase).ToList();
        var modsync = files.FirstOrDefault(f => Path.GetFileName(f).Contains("modsync", StringComparison.OrdinalIgnoreCase));
        AddModRow("ModSync", "1.4.0", modsync == null ? "必装 · 未安装" : "必装 · 已安装", true, true, modsync);
        foreach (var file in files.Where(f => !Path.GetFileName(f).Contains("modsync", StringComparison.OrdinalIgnoreCase)))
        {
            var enabled = file.EndsWith(".jar", StringComparison.OrdinalIgnoreCase);
            var name = Path.GetFileName(file).Replace(".jar.disabled", "").Replace(".jar", "");
            AddModRow(name, enabled ? "已启用" : "已禁用", enabled ? "普通" : "已禁用", false, enabled, file);
        }
        ModCountText.Text = $"共 {files.Count + 1} 个模组 · 其中 ModSync 为必装";
        InstalledCountText.Text = (files.Count + 1).ToString();
        HeroModCount.Text = $"{files.Count + 1} 个模组";
    }

    private void AddModRow(string name, string version, string badge, bool required, bool enabled, string? filePath)
    {
        var border = new Border
        {
            Background = required ? new SolidColorBrush(Color.FromRgb(0xE9, 0xFA, 0xF4)) : new SolidColorBrush(Colors.White),
            BorderBrush = required ? new SolidColorBrush(Color.FromRgb(0xC9, 0xEE, 0xE1)) : new SolidColorBrush(Color.FromRgb(0xE4, 0xF2, 0xEC)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(14, 10, 14, 10),
            Margin = new Thickness(16, 3, 16, 3),
        };
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        BitmapImage? iconImage = filePath == null
            ? new BitmapImage(new Uri("pack://application:,,,/Assets/modsync.png"))
            : ModIconService.LoadFromJar(filePath);
        var icon = new Border
        {
            Width = 34, Height = 34, CornerRadius = new CornerRadius(9),
            Background = new SolidColorBrush(Color.FromRgb(0xE4, 0xF8, 0xF1)),
            Margin = new Thickness(0, 0, 10, 0),
            VerticalAlignment = VerticalAlignment.Center,
        };
        if (iconImage != null)
        {
            icon.Background = new ImageBrush(iconImage) { Stretch = Stretch.UniformToFill };
        }
        else
        {
            icon.Child = new TextBlock
            {
                Text = string.IsNullOrWhiteSpace(name) ? "M" : name[..1].ToUpperInvariant(),
                FontSize = 13, FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(0x17, 0x88, 0x6C)),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
            };
        }
        grid.Children.Add(icon);

        var info = new StackPanel { Margin = new Thickness(0) };
        info.Children.Add(new TextBlock { Text = name, FontSize = 12.5, FontWeight = FontWeights.SemiBold });
        info.Children.Add(new TextBlock { Text = version, FontSize = 10, Foreground = new SolidColorBrush(Color.FromRgb(0x75, 0x89, 0x81)), Margin = new Thickness(0, 2, 0, 0) });
        Grid.SetColumn(info, 1);
        grid.Children.Add(info);

        var pill = new Border
        {
            Background = required ? new SolidColorBrush(Color.FromRgb(0x2F, 0xBE, 0x9D)) : new SolidColorBrush(Color.FromRgb(0xF0, 0xF7, 0xF4)),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(9, 4, 9, 4),
            VerticalAlignment = VerticalAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Right,
        };
        pill.Child = new TextBlock
        {
            Text = badge,
            FontSize = 10,
            Foreground = required ? new SolidColorBrush(Colors.White) : new SolidColorBrush(Color.FromRgb(0x75, 0x89, 0x81)),
            FontWeight = FontWeights.SemiBold,
        };
        Grid.SetColumn(pill, 3);
        grid.Children.Add(pill);

        if (filePath != null)
        {
            var actions = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 8, 0) };
            var toggle = SmallButton(enabled ? "禁用" : "启用");
            toggle.Click += (_, _) => ToggleModFile(filePath);
            actions.Children.Add(toggle);
            if (_updates.ContainsKey(filePath))
            {
                var update = SmallButton("更新");
                update.Click += async (_, _) => await UpdateModAsync(filePath);
                actions.Children.Add(update);
            }
            var delete = SmallButton("删除");
            delete.Foreground = new SolidColorBrush(Color.FromRgb(0xC0, 0x55, 0x55));
            delete.Click += (_, _) => DeleteModFile(filePath);
            actions.Children.Add(delete);
            Grid.SetColumn(actions, 2);
            grid.Children.Add(actions);
        }
        border.Child = grid;
        ModsStack.Children.Add(border);
    }

    private Button SmallButton(string text) => new()
    {
        Content = text,
        FontSize = 10,
        Padding = new Thickness(8, 4, 8, 4),
        Margin = new Thickness(4, 0, 0, 0),
        Background = new SolidColorBrush(Color.FromRgb(0xF0, 0xF7, 0xF4)),
        Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0x5A, 0x4E)),
        BorderThickness = new Thickness(0),
        Cursor = Cursors.Hand,
        Style = (Style)FindResource("SmallPill"),
    };

    private void ToggleModFile(string filePath)
    {
        try
        {
            var target = filePath.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)
                ? filePath + ".disabled"
                : filePath[..^".disabled".Length];
            File.Move(filePath, target);
            LoadMods();
        }
        catch (Exception ex)
        {
            ShowToast("切换失败：" + ex.Message);
        }
    }

    private async Task UpdateModAsync(string filePath)
    {
        var fileName = Path.GetFileName(filePath);
        var query = System.Text.RegularExpressions.Regex.Replace(
            Path.GetFileNameWithoutExtension(fileName).Replace(".jar", ""),
            @"[-_](?:v?\d[\w.+-]*)$", "");
        var loader = (DownloadLoader.SelectedItem as ComboBoxItem)?.Content?.ToString()?.ToLowerInvariant() ?? "neoforge";
        var mc = (DownloadVersion.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "1.21.1";
        try
        {
            ShowToast("正在查找更新…");
            var hits = await ModrinthService.SearchAsync(query, loader, mc);
            var hit = hits.FirstOrDefault();
            if (hit == null)
            {
                ShowToast("没有找到对应模组更新");
                return;
            }
            await InstallProjectAsync(hit, new Button(), loader, mc);
            if (File.Exists(filePath))
            {
                File.Move(filePath, filePath + ".disabled");
            }
            LoadMods();
        }
        catch (Exception ex)
        {
            ShowToast("更新失败：" + ex.Message);
        }
    }

    private void EnsureBundledModSync()
    {
        try
        {
            var modsDir = ActiveModsDir;
            Directory.CreateDirectory(modsDir);
            if (Directory.GetFiles(modsDir, "*modsync*.jar").Length > 0) return;
            var asset = _activeVersion.StartsWith("26.3", StringComparison.OrdinalIgnoreCase)
                ? "pack://application:,,,/Assets/ModSync-26.3.jar"
                : _activeVersion.StartsWith("1.21.1", StringComparison.OrdinalIgnoreCase)
                    ? "pack://application:,,,/Assets/ModSync-1.21.1.jar"
                    : null;
            if (asset == null) return;
            var resource = Application.GetResourceStream(new Uri(asset));
            if (resource == null) return;
            var target = Path.Combine(modsDir, $"modsync-neoforge-{_activeVersion}-1.4.0.jar");
            using var input = resource.Stream;
            using var output = File.Create(target);
            input.CopyTo(output);
            ShowToast("已自动安装 ModSync 必装模组");
        }
        catch
        {
            // The mods page still shows ModSync as required if bundling fails.
        }
    }

    private void UpdateHeroSettings()
    {
        if (HeroMemoryChip == null) return;
        HeroMemoryChip.Text = $"{_settings.MemoryMb} MB";
        HeroJavaChip.Text = string.IsNullOrWhiteSpace(_settings.JavaPath) || _settings.JavaPath == "自动选择"
            ? "Java 自动"
            : "自定义 Java";
    }

    private void Memory_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_loadingSettings) return;
        if (MemoryBox.SelectedItem is not ComboBoxItem item || !int.TryParse(item.Content?.ToString(), out var mb)) return;
        _settings.MemoryMb = mb;
        AppSettingsStore.Save(_settings);
        UpdateHeroSettings();
    }

    private void Java_LostFocus(object sender, RoutedEventArgs e)
    {
        var text = JavaBox.Text.Trim();
        _settings.JavaPath = text == "自动选择" ? null : text;
        AppSettingsStore.Save(_settings);
        UpdateHeroSettings();
    }

    private void BrowseJava_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Filter = "Java|java.exe;javaw.exe|所有文件|*.*",
            Title = "选择 java.exe",
        };
        if (dialog.ShowDialog(this) == true)
        {
            JavaBox.Text = dialog.FileName;
            _settings.JavaPath = dialog.FileName;
            AppSettingsStore.Save(_settings);
            UpdateHeroSettings();
        }
    }

    private void SortMods_Click(object sender, RoutedEventArgs e)
    {
        _sortAZ = !_sortAZ;
        LoadMods();
    }

    private async void CheckUpdates_Click(object sender, RoutedEventArgs e)
    {
        var modsDir = ActiveModsDir;
        if (!Directory.Exists(modsDir)) return;
        var loader = (DownloadLoader.SelectedItem as ComboBoxItem)?.Content?.ToString()?.ToLowerInvariant() ?? "neoforge";
        var mc = (DownloadVersion.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "1.21.1";
        _updates.Clear();
        ShowToast("正在检查模组更新…");
        foreach (var file in Directory.GetFiles(modsDir, "*.jar").Where(f => !Path.GetFileName(f).Contains("modsync", StringComparison.OrdinalIgnoreCase)))
        {
            try
            {
                var query = Path.GetFileNameWithoutExtension(file);
                var hits = await ModrinthService.SearchAsync(query, loader, mc);
                var hit = hits.FirstOrDefault();
                if (hit == null) continue;
                var versions = await ModrinthService.GetVersionsAsync(hit.ProjectId, loader, mc);
                var latest = versions.FirstOrDefault()?.Files.FirstOrDefault(f => f.Primary)?.Filename;
                if (!string.IsNullOrWhiteSpace(latest) &&
                    !string.Equals(Path.GetFileName(file), latest, StringComparison.OrdinalIgnoreCase))
                {
                    _updates[file] = hit;
                }
            }
            catch
            {
                // Ignore a single failed lookup.
            }
        }
        LoadMods();
        ShowToast(_updates.Count > 0 ? $"发现 {_updates.Count} 个可更新模组" : "所有模组已是最新");
    }

    private void DeleteModFile(string filePath)
    {
        if (MessageBox.Show(this, $"删除 {Path.GetFileName(filePath)}？", "删除模组",
                MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        try
        {
            File.Delete(filePath);
            _updates.Remove(filePath);
            LoadMods();
        }
        catch (Exception ex)
        {
            ShowToast("删除失败：" + ex.Message);
        }
    }

    private void RefreshMods_Click(object sender, RoutedEventArgs e) => LoadMods();

    private void OpenModsFolder_Click(object sender, RoutedEventArgs e)
    {
        var dir = ActiveModsDir;
        Directory.CreateDirectory(dir);
        Process.Start(new ProcessStartInfo("explorer.exe", dir) { UseShellExecute = true });
    }

    private async void SearchMods_Click(object sender, RoutedEventArgs e)
    {
        var loader = (DownloadLoader.SelectedItem as ComboBoxItem)?.Content?.ToString()?.ToLowerInvariant() ?? "neoforge";
        var mc = (DownloadVersion.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "1.21.1";
        DownloadStatus.Text = "正在搜索 Modrinth…";
        DownloadResults.Children.Clear();
        try
        {
            var hits = await ModrinthService.SearchAsync(SearchBox.Text.Trim(), loader, mc);
            _lastHits = hits;
            DownloadStatus.Text = hits.Count == 0 ? "没有找到结果。" : $"找到 {hits.Count} 个结果 · {loader} {mc}";
            foreach (var hit in hits)
            {
                DownloadResults.Children.Add(BuildDownloadCard(hit, loader, mc));
            }
        }
        catch (Exception ex)
        {
            DownloadStatus.Text = "搜索失败：" + ex.Message;
            ShowToast("Modrinth 搜索失败：" + ex.Message);
        }
    }

    private Border BuildDownloadCard(ModrinthHit hit, string loader, string mc)
    {
        var card = new Border
        {
            Width = 268,
            Height = 190,
            Margin = new Thickness(0, 0, 12, 12),
            Background = new SolidColorBrush(Colors.White),
            BorderBrush = new SolidColorBrush(Color.FromRgb(0xDC, 0xED, 0xE6)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(16),
            Padding = new Thickness(16),
        };
        var panel = new StackPanel();
        var head = new StackPanel { Orientation = Orientation.Horizontal };
        var icon = new Border
        {
            Width = 38, Height = 38, CornerRadius = new CornerRadius(11),
            Background = new SolidColorBrush(Color.FromRgb(0xE4, 0xF8, 0xF1)),
        };
        var letter = new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(hit.Title) ? "M" : hit.Title[..1].ToUpperInvariant(),
            FontSize = 16, FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(Color.FromRgb(0x17, 0x88, 0x6C)),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        icon.Child = letter;
        head.Children.Add(icon);
        if (!string.IsNullOrWhiteSpace(hit.IconUrl))
        {
            _ = ApplyIconAsync(icon, letter, hit.IconUrl);
        }
        head.Children.Add(new TextBlock
        {
            Text = hit.Title,
            FontSize = 13.5,
            FontWeight = FontWeights.SemiBold,
            Margin = new Thickness(9, 0, 0, 0),
            VerticalAlignment = VerticalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis,
        });
        panel.Children.Add(head);
        panel.Children.Add(new TextBlock
        {
            Text = hit.Description,
            FontSize = 10.5,
            Foreground = new SolidColorBrush(Color.FromRgb(0x75, 0x89, 0x81)),
            TextWrapping = TextWrapping.Wrap,
            TextTrimming = TextTrimming.CharacterEllipsis,
            Height = 42,
            Margin = new Thickness(0, 9, 0, 0),
        });
        var foot = new Grid { Margin = new Thickness(0, 8, 0, 0) };
        foot.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        foot.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        foot.Children.Add(new TextBlock
        {
            Text = FormatDownloads(hit.Downloads),
            FontSize = 10,
            Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA9, 0xA1)),
            VerticalAlignment = VerticalAlignment.Center,
        });
        var install = new Button
        {
            Content = "安装",
            Padding = new Thickness(16, 6, 16, 6),
            Background = new SolidColorBrush(Color.FromRgb(0xE4, 0xF8, 0xF1)),
            Foreground = new SolidColorBrush(Color.FromRgb(0x17, 0x88, 0x6C)),
            BorderThickness = new Thickness(0),
            FontSize = 11,
            FontWeight = FontWeights.SemiBold,
            Cursor = Cursors.Hand,
        };
        Grid.SetColumn(install, 1);
        install.Click += async (_, _) => await InstallProjectAsync(hit, install, loader, mc);
        foot.Children.Add(install);
        panel.Children.Add(foot);
        card.Child = panel;
        return card;
    }

    private static async Task ApplyIconAsync(Border icon, TextBlock letter, string url)
    {
        var image = await ModIconService.LoadFromUrlAsync(url);
        if (image == null) return;
        await icon.Dispatcher.InvokeAsync(() =>
        {
            icon.Background = new ImageBrush(image) { Stretch = Stretch.UniformToFill };
            letter.Visibility = Visibility.Collapsed;
        });
    }

    private async Task InstallProjectAsync(ModrinthHit hit, Button button, string loader, string mc)
    {
        button.IsEnabled = false;
        var original = button.Content;
        button.Content = "安装中…";
        try
        {
            var modsDir = ActiveModsDir;
            var log = new Action<string>(message => Dispatcher.Invoke(() => DownloadStatus.Text = message));
            var report = await ModrinthService.InstallAsync(hit, loader, mc, modsDir, log);
            DownloadStatus.Text = $"安装完成：新增 {report.Installed.Count} 个，已存在 {report.AlreadyPresent.Count} 个";
            ShowToast("安装完成");
            LoadMods();
        }
        catch (Exception ex)
        {
            DownloadStatus.Text = "安装失败：" + ex.Message;
            ShowToast("安装失败：" + ex.Message);
        }
        finally
        {
            button.IsEnabled = true;
            button.Content = original;
        }
    }

    private static string FormatDownloads(long value)
    {
        if (value >= 1_000_000) return $"↓ {(value / 1_000_000.0):0.0}M";
        if (value >= 1_000) return $"↓ {(value / 1_000.0):0.0}K";
        return $"↓ {value}";
    }

    private void LoadVersions()
    {
        var versions = MinecraftLauncher.ListVersions(MinecraftLauncher.DefaultGameDir);
        VersionBox.ItemsSource = versions;
        if (versions.Count == 0)
        {
            InstanceTitle.Text = "没有找到版本";
            LaunchButton.IsEnabled = false;
            LaunchStatus.Text = "请先在 .minecraft/versions 中安装一个版本";
            return;
        }
        VersionBox.SelectedItem = versions.Contains("26.3") ? "26.3" : versions[0];
        _activeVersion = VersionBox.SelectedItem as string ?? versions[0];
    }

    private void VersionBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (VersionBox.SelectedItem is not string version) return;
        _activeVersion = version;
        InstanceTitle.Text = version;
        HeroSubtitle.Text = $"准备好进入 {version} 了吗？";
        if (_activeInstance != null)
        {
            if (_activeInstance.VersionId != version) _activeInstance.LoaderVersionId = null;
            _activeInstance.VersionId = version;
            InstanceStore.Save(_instances);
        }
        PrepareVersionMods();
        EnsureBundledModSync();
        LoadMods();
    }

    private void InstanceBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (InstanceBox.SelectedItem is not InstanceProfile profile) return;
        _activeInstance = profile;
        _activeVersion = profile.VersionId;
        _loadingInstance = true;
        if (VersionBox.Items.Contains(profile.VersionId))
        {
            VersionBox.SelectedItem = profile.VersionId;
        }
        ApplyLoaderSelection(profile.Loader);
        _loadingInstance = false;
        EnsureBundledModSync();
        LoadMods();
        RefreshInstanceCards();
        UpdateActiveInstanceUi();
        if (DownloadView.Visibility == Visibility.Visible) SyncDownloadFilters();
    }

    private void LoadInstances()
    {
        _instances = InstanceStore.Load();
        if (_instances.Count == 0)
        {
            var version = VersionBox.SelectedItem as string ?? "26.3";
            var profile = InstanceStore.Create("默认实例", version);
            _instances.Add(profile);
            InstanceStore.Save(_instances);
        }
        InstanceBox.ItemsSource = _instances;
        InstanceBox.SelectedIndex = 0;
        _activeInstance = _instances[0];
        _activeVersion = _activeInstance.VersionId;
        _loadingInstance = true;
        if (VersionBox.Items.Contains(_activeVersion)) VersionBox.SelectedItem = _activeVersion;
        ApplyLoaderSelection(_activeInstance.Loader);
        _loadingInstance = false;
        EnsureBundledModSync();
        LoadMods();
    }

    private async Task LoadMinecraftVersionsAsync()
    {
        try
        {
            McVersionStatus.Text = "正在读取 Mojang 版本列表…";
            var versions = await VersionDownloader.GetVersionsAsync();
            McVersionBox.ItemsSource = versions;
            McVersionBox.SelectedItem = versions.FirstOrDefault(v => v.Id == _activeVersion) ?? versions.FirstOrDefault();
            McVersionStatus.Text = $"共 {versions.Count} 个正式版本，可下载到版本目录";
        }
        catch (Exception ex)
        {
            McVersionStatus.Text = "版本列表读取失败：" + ex.Message;
        }
    }

    private async void DownloadMinecraftVersion_Click(object sender, RoutedEventArgs e)
    {
        if (McVersionBox.SelectedItem is not MinecraftVersionEntry entry) return;
        try
        {
            McVersionStatus.Text = $"正在下载 {entry.Id}…";
            await VersionDownloader.DownloadAsync(entry.Id, MinecraftLauncher.DefaultGameDir,
                message => Dispatcher.Invoke(() => McVersionStatus.Text = message));
            LoadVersions();
            McVersionStatus.Text = $"{entry.Id} 已下载并加入版本列表";
            ShowToast($"{entry.Id} 下载完成");
        }
        catch (Exception ex)
        {
            McVersionStatus.Text = "下载失败：" + ex.Message;
        }
    }

    private void UpdateActiveInstanceUi()
    {
        if (HeroInstanceText == null) return;
        if (_activeInstance == null)
        {
            HeroInstanceText.Text = "还没有实例";
            return;
        }
        var loader = string.IsNullOrWhiteSpace(_activeInstance.Loader) ? "neoforge" : _activeInstance.Loader;
        HeroInstanceText.Text = $"当前实例：{_activeInstance.Name} · {_activeInstance.VersionId} · {loader}";
    }

    private void OpenLibrary_Click(object sender, RoutedEventArgs e) => NavLibrary.IsChecked = true;

    private void RefreshInstanceCards()
    {
        if (InstanceCards == null) return;
        InstanceCards.Children.Clear();
        var filter = InstanceSearchBox?.Text?.Trim() ?? "";
        var visible = _instances
            .Where(i => string.IsNullOrWhiteSpace(filter) || i.Name.Contains(filter, StringComparison.OrdinalIgnoreCase) || i.VersionId.Contains(filter, StringComparison.OrdinalIgnoreCase))
            .ToList();
        InstanceCountText.Text = $"{visible.Count} 个实例";
        var colors = new[] { "#6B4BD6", "#E9B949", "#E04F6D", "#8B5CF6", "#C9903A", "#2FBE9D" };
        for (var index = 0; index < visible.Count; index++)
        {
            var profile = visible[index];
            var isActive = ReferenceEquals(profile, _activeInstance);
            var card = new Border
            {
                Width = 250,
                Margin = new Thickness(0, 0, 14, 14),
                Background = new SolidColorBrush(Colors.White),
                BorderBrush = new SolidColorBrush(isActive ? Color.FromRgb(0x2F, 0xBE, 0x9D) : Color.FromRgb(0xD8, 0xEC, 0xE4)),
                BorderThickness = new Thickness(isActive ? 2 : 1),
                CornerRadius = new CornerRadius(16),
                Padding = new Thickness(10),
                Cursor = Cursors.Hand,
            };
            var panel = new StackPanel();
            var cover = new Border
            {
                Height = 92,
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString(colors[index % colors.Length])),
            };
            var coverText = new StackPanel { HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(14, 0, 0, 0) };
            coverText.Children.Add(new TextBlock { Text = "ModSync", FontSize = 10, Foreground = new SolidColorBrush(Color.FromArgb(0xCC, 0xFF, 0xFF, 0xFF)) });
            coverText.Children.Add(new TextBlock { Text = profile.VersionId, FontSize = 23, FontWeight = FontWeights.Bold, Foreground = new SolidColorBrush(Colors.White), Margin = new Thickness(0, 2, 0, 0) });
            cover.Child = coverText;
            panel.Children.Add(cover);
            var titleRow = new Grid { Margin = new Thickness(4, 10, 4, 0) };
            titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            titleRow.Children.Add(new TextBlock { Text = profile.Name, FontSize = 13, FontWeight = FontWeights.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis });
            if (isActive)
            {
                var pill = new Border
                {
                    Background = new SolidColorBrush(Color.FromRgb(0xE4, 0xF8, 0xF1)), CornerRadius = new CornerRadius(7),
                    Padding = new Thickness(7, 2, 7, 2),
                    Child = new TextBlock { Text = "使用中", FontSize = 9, Foreground = new SolidColorBrush(Color.FromRgb(0x17, 0x88, 0x6C)) },
                };
                Grid.SetColumn(pill, 1);
                titleRow.Children.Add(pill);
            }
            var delete = SmallButton("删除");
            delete.FontSize = 9;
            delete.Foreground = new SolidColorBrush(Color.FromRgb(0xC0, 0x55, 0x55));
            delete.Margin = new Thickness(6, 0, 0, 0);
            delete.Click += (_, args) => { args.Handled = true; DeleteInstance(profile); };
            Grid.SetColumn(delete, 2);
            titleRow.Children.Add(delete);
            panel.Children.Add(titleRow);
            panel.Children.Add(new TextBlock { Text = $"{profile.VersionId} · {profile.Loader}", FontSize = 10, Foreground = new SolidColorBrush(Color.FromRgb(0x75, 0x89, 0x81)), Margin = new Thickness(4, 3, 4, 4) });
            card.Child = panel;
            card.MouseLeftButtonUp += (_, _) => SelectInstance(profile);
            InstanceCards.Children.Add(card);
        }
    }

    private void DeleteInstance(InstanceProfile profile)
    {
        if (MessageBox.Show(this, $"删除实例「{profile.Name}」及其模组目录？", "删除实例",
                MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        _instances.Remove(profile);
        try
        {
            var root = Path.GetFullPath(Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ModSyncLauncher", "instances"));
            var target = Path.GetFullPath(profile.GameDir);
            if (target.StartsWith(root, StringComparison.OrdinalIgnoreCase) && Directory.Exists(target))
            {
                Directory.Delete(target, true);
            }
        }
        catch
        {
            // Leave the folder if a mod or file is locked.
        }
        if (_instances.Count == 0)
        {
            _instances.Add(InstanceStore.Create("默认实例", _activeVersion, importGlobal: false));
        }
        InstanceStore.Save(_instances);
        _activeInstance = _instances[0];
        _activeVersion = _activeInstance.VersionId;
        InstanceBox.ItemsSource = null;
        InstanceBox.ItemsSource = _instances;
        InstanceBox.SelectedItem = _activeInstance;
        RefreshInstanceCards();
        ShowToast("实例已删除");
    }

    private void InstanceSearch_TextChanged(object sender, TextChangedEventArgs e) => RefreshInstanceCards();

    private void SelectInstance(InstanceProfile profile)
    {
        InstanceBox.SelectedItem = profile;
        NavStart.IsChecked = true;
    }

    private void ApplyLoaderSelection(string? loader)
    {
        var normalized = string.IsNullOrWhiteSpace(loader) ? "neoforge" : loader;
        foreach (ComboBoxItem item in LoaderBox.Items)
        {
            if (string.Equals(item.Content?.ToString(), normalized, StringComparison.OrdinalIgnoreCase))
            {
                LoaderBox.SelectedItem = item;
                return;
            }
        }
    }

    private void Loader_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_loadingInstance || _activeInstance == null) return;
        var loader = (LoaderBox.SelectedItem as ComboBoxItem)?.Content?.ToString()?.ToLowerInvariant() ?? "neoforge";
        if (_activeInstance.Loader != loader) _activeInstance.LoaderVersionId = null;
        _activeInstance.Loader = loader;
        InstanceStore.Save(_instances);
        SyncDownloadFilters();
        RefreshInstanceCards();
        UpdateActiveInstanceUi();
    }

    private void SyncDownloadFilters()
    {
        if (_activeInstance == null || DownloadLoader == null || DownloadVersion == null) return;
        var loader = string.IsNullOrWhiteSpace(_activeInstance.Loader) ? "neoforge" : _activeInstance.Loader;
        foreach (ComboBoxItem item in DownloadLoader.Items)
        {
            if (string.Equals(item.Content?.ToString(), loader, StringComparison.OrdinalIgnoreCase))
            {
                DownloadLoader.SelectedItem = item;
                break;
            }
        }
        var version = _activeInstance.VersionId;
        var exists = false;
        foreach (ComboBoxItem item in DownloadVersion.Items)
        {
            if (string.Equals(item.Content?.ToString(), version, StringComparison.Ordinal))
            {
                DownloadVersion.SelectedItem = item;
                exists = true;
                break;
            }
        }
        if (!exists)
        {
            var item = new ComboBoxItem { Content = version };
            DownloadVersion.Items.Add(item);
            DownloadVersion.SelectedItem = item;
        }
        DownloadContextText.Text = $"当前实例：{_activeInstance.Name} · {version} · {loader}";
    }

    private void NewInstanceDialog_Click(object sender, RoutedEventArgs e)
    {
        var versions = VersionBox.Items.Cast<string>().ToList();
        if (versions.Count == 0)
        {
            ShowToast("还没有可用的 Minecraft 版本");
            return;
        }
        var dialog = new InstanceWindow(versions, _activeVersion, $"实例 {_instances.Count + 1}") { Owner = this };
        if (dialog.ShowDialog() != true) return;
        var profile = InstanceStore.Create(dialog.InstanceName, dialog.VersionId, importGlobal: false, loader: dialog.LoaderId);
        _instances.Add(profile);
        InstanceStore.Save(_instances);
        InstanceBox.ItemsSource = null;
        InstanceBox.ItemsSource = _instances;
        InstanceBox.SelectedItem = profile;
        RefreshInstanceCards();
        NavLibrary.IsChecked = true;
        ShowToast("实例已创建，可以单独安装模组了");
    }

    private void PrepareVersionMods()
    {
        Directory.CreateDirectory(ActiveModsDir);
        if (_activeInstance?.ImportGlobalMods != true) return;
        if (Directory.EnumerateFiles(ActiveModsDir, "*.jar*").Any()) return;
        var global = Path.Combine(MinecraftLauncher.DefaultGameDir, "mods");
        if (!Directory.Exists(global)) return;
        foreach (var file in Directory.GetFiles(global, "*.jar*"))
        {
            try { File.Copy(file, Path.Combine(ActiveModsDir, Path.GetFileName(file)), false); }
            catch { }
        }
    }

    private async void Launch_Click(object sender, RoutedEventArgs e)
    {
        if (_launching) return;
        if (VersionBox.SelectedItem is not string version)
        {
            ShowToast("没有选择版本");
            return;
        }

        _launching = true;
        LaunchButton.IsEnabled = false;
        LaunchButton.Content = "启动中…";
        LaunchProgress.Visibility = Visibility.Visible;
        LaunchStatus.Text = "正在准备启动…";
        try
        {
            var launchVersion = version;
            if (_activeInstance?.Loader == "fabric")
            {
                var fabricDir = Path.Combine(MinecraftLauncher.DefaultGameDir, "versions", _activeInstance.LoaderVersionId ?? "");
                if (string.IsNullOrWhiteSpace(_activeInstance.LoaderVersionId) || !Directory.Exists(fabricDir))
                {
                    LaunchStatus.Text = "正在安装 Fabric Loader…";
                    launchVersion = await FabricInstaller.EnsureAsync(version, MinecraftLauncher.DefaultGameDir,
                        message => Dispatcher.Invoke(() => LaunchStatus.Text = message));
                    _activeInstance.LoaderVersionId = launchVersion;
                    InstanceStore.Save(_instances);
                }
                else
                {
                    launchVersion = _activeInstance.LoaderVersionId;
                }
            }
            var request = new LaunchRequest(
                launchVersion,
                _activeInstance?.GameDir ?? MinecraftLauncher.DefaultGameDir,
                MinecraftLauncher.DefaultGameDir,
                _account,
                _settings.MemoryMb,
                string.IsNullOrWhiteSpace(_settings.JavaPath) || _settings.JavaPath == "自动选择" ? null : _settings.JavaPath,
                message => Dispatcher.Invoke(() =>
                {
                    if (message.Length > 90) message = message[..90] + "…";
                    LaunchStatus.Text = message;
                    try { File.AppendAllText(Path.Combine(Environment.CurrentDirectory, "launcher-wpf", "launch.log"), message + Environment.NewLine); } catch { }
                }));
            var process = await MinecraftLauncher.LaunchAsync(request);
            LastLaunchText.Text = DateTime.Now.ToString("HH:mm");
            LastLaunchVersion.Text = launchVersion;
            LaunchStatus.Text = $"游戏已启动 · PID {process.Id}";
            ShowToast($"Minecraft 已启动，PID {process.Id}");
        }
        catch (Exception ex)
        {
            LaunchStatus.Text = "启动失败";
            MessageBox.Show(this, ex.Message, "启动失败", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally
        {
            _launching = false;
            LaunchButton.IsEnabled = true;
            LaunchButton.Content = "▶   启动游戏";
            LaunchProgress.Visibility = Visibility.Collapsed;
        }
    }

    private void ApplyAccount()
    {
        TopName.Text = _account.Name;
        HeroGreeting.Text = $"你好，{_account.Name}";
        SideName.Text = _account.Name;
        var status = _account.Type == "微软账号"
            ? (string.IsNullOrWhiteSpace(_account.AccessToken) ? "未登录" : "已完成登录")
            : _account.Type;
        TopType.Text = status;
        SideType.Text = status;

        var image = AccountStore.LoadAvatar(_account.AvatarPath);
        if (image == null)
        {
            var fallback = new BitmapImage(new Uri("pack://application:,,,/Assets/avatar.png"));
            image = fallback;
        }
        var brush = new ImageBrush(image) { Stretch = Stretch.UniformToFill };
        TopAvatar.Background = brush;
        SideAvatar.Background = brush;
    }

    private void SaveScreenshot()
    {
        try
        {
            var width = Math.Max(1, (int)ActualWidth);
            var height = Math.Max(1, (int)ActualHeight);
            var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
            bitmap.Render(this);
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(bitmap));
            var outArg = Environment.GetCommandLineArgs().FirstOrDefault(a => a.StartsWith("--out="));
            var name = outArg is null ? "modern-preview.png" : outArg["--out=".Length..];
            var file = Path.Combine(Environment.CurrentDirectory, "launcher-wpf", name);
            Directory.CreateDirectory(Path.GetDirectoryName(file)!);
            using var stream = File.Create(file);
            encoder.Save(stream);
            Close();
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "screenshot failed");
        }
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            ToggleMaximize();
            return;
        }
        try { DragMove(); } catch (InvalidOperationException) { }
    }

    private void ToggleMaximize()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) => ToggleMaximize();

    private void Close_Click(object sender, RoutedEventArgs e) => Close();


    private void Account_Click(object sender, RoutedEventArgs e)
    {
        if (_account.Type == "微软账号" && string.IsNullOrWhiteSpace(_account.AccessToken))
        {
            var login = new MicrosoftLoginWindow { Owner = this };
            if (login.ShowDialog() == true && login.Result != null)
            {
                var result = login.Result;
                _account.Name = result.Name;
                _account.Type = "微软账号";
                _account.Uuid = result.Uuid;
                _account.AccessToken = result.AccessToken;
                _account.RefreshToken = result.RefreshToken;
                _account.ExpiresAt = result.ExpiresAt;
                _account.AvatarPath = result.AvatarPath;
                AccountStore.Save(_account);
                ApplyAccount();
                ShowToast("微软账号登录成功");
            }
            return;
        }
        var dialog = new AccountWindow(_account) { Owner = this };
        if (dialog.ShowDialog() == true)
        {
            _account = dialog.Profile;
            ApplyAccount();
            ShowToast("账号已保存");
        }
    }

    private void ShowToast(string text)
    {
        ToastText.Text = text;
        Toast.Visibility = Visibility.Visible;
        _toastTimer?.Stop();
        _toastTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
        _toastTimer.Tick += (_, _) =>
        {
            _toastTimer?.Stop();
            Toast.Visibility = Visibility.Collapsed;
        };
        _toastTimer.Start();
    }
}
