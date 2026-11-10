using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace ModSyncLauncher;

public partial class MainWindow : Window
{
    private DispatcherTimer? _toastTimer;

    public MainWindow()
    {
        InitializeComponent();
        if (Environment.GetCommandLineArgs().Contains("--screenshot"))
        {
            Loaded += async (_, _) =>
            {
                var tabArg = Environment.GetCommandLineArgs().FirstOrDefault(a => a.StartsWith("--tab="));
                if (tabArg is not null)
                {
                    var key = tabArg["--tab=".Length..];
                    ShowTab(key);
                    if (key == "settings") TabSettings.IsChecked = true;
                    else if (key == "download") TabDownload.IsChecked = true;
                    else TabStart.IsChecked = true;
                }
                await Task.Delay(250);
                SaveScreenshot();
            };
        }
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
            var fileName = outArg is null ? "preview.png" : outArg["--out=".Length..];
            var file = Path.Combine(Environment.CurrentDirectory, "launcher-wpf", fileName);
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

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        ShowTab("start");
    }

    private void TopTab_Checked(object sender, RoutedEventArgs e)
    {
        if (sender is not RadioButton tab || tab.Tag is not string key) return;
        if (PageStart is null) return;
        ShowTab(key);
    }

    private void ShowTab(string key)
    {
        PageStart.Visibility = key == "start" ? Visibility.Visible : Visibility.Collapsed;
        PageDownload.Visibility = key == "download" ? Visibility.Visible : Visibility.Collapsed;
        PageSettings.Visibility = key == "settings" ? Visibility.Visible : Visibility.Collapsed;
        PageMore.Visibility = key == "more" ? Visibility.Visible : Visibility.Collapsed;

    }

    private void Search_Click(object sender, RoutedEventArgs e)
    {
        ShowToast("搜索示例：下一版接入 Modrinth 实时结果。");
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            ToggleMaximize();
            return;
        }

        try
        {
            DragMove();
        }
        catch (InvalidOperationException)
        {
            // Mouse button was released before DragMove started.
        }
    }

    private void Minimize_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    private void Maximize_Click(object sender, RoutedEventArgs e)
    {
        ToggleMaximize();
    }

    private void ToggleMaximize()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void NewInstance_Click(object sender, RoutedEventArgs e)
    {
        ShowToast("新建实例：下一版接入创建向导。");
    }

    private void OpenFolder_Click(object sender, RoutedEventArgs e)
    {
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft");
        try
        {
            Process.Start(new ProcessStartInfo("explorer.exe", path) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            ShowToast($"无法打开目录：{ex.Message}");
        }
    }

    private void Launch_Click(object sender, RoutedEventArgs e)
    {
        ShowToast("界面预览：启动逻辑将在接入启动器后启用。");
    }

    private void CheckUpdate_Click(object sender, RoutedEventArgs e)
    {
        ShowToast("正在检查更新…  (界面预览)");
    }

    private async void ShowToast(string message)
    {
        ToastText.Text = message;
        Toast.Visibility = Visibility.Visible;
        _toastTimer?.Stop();
        _toastTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3.2) };
        _toastTimer.Tick += (_, _) =>
        {
            _toastTimer?.Stop();
            Toast.Visibility = Visibility.Collapsed;
        };
        _toastTimer.Start();
        await Task.CompletedTask;
    }
}
