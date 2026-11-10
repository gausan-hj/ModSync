using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using ModSyncLauncher.Services;

namespace ModSyncLauncher;

public partial class MicrosoftLoginWindow : Window
{
    private readonly CancellationTokenSource _cts = new();
    private MicrosoftDeviceCode? _deviceCode;
    private bool _started;

    public MicrosoftLoginResult? Result { get; private set; }

    public MicrosoftLoginWindow()
    {
        InitializeComponent();
        Loaded += async (_, _) =>
        {
            if (_started) return;
            _started = true;
            await BeginLoginAsync();
        };
    }

    private async Task BeginLoginAsync()
    {
        try
        {
            StatusText.Text = "正在获取登录代码…";
            _deviceCode = await MicrosoftAuthService.StartDeviceCodeAsync(_cts.Token);
            CodeText.Text = _deviceCode.UserCode;
            LinkText.Text = _deviceCode.VerificationUri;
            StatusText.Text = "已生成验证代码，请按浏览器提示登录…";
            try { Process.Start(new ProcessStartInfo(_deviceCode.VerificationUri) { UseShellExecute = true }); } catch { }
            Activate();

            var microsoftToken = await MicrosoftAuthService.WaitForAccessTokenAsync(_deviceCode, _cts.Token);
            StatusText.Text = "正在登录 Minecraft…";
            Result = await MicrosoftAuthService.CompleteAsync(microsoftToken, _cts.Token);
            StatusText.Text = $"登录成功：{Result.Name}";
            DialogResult = true;
            Close();
        }
        catch (OperationCanceledException)
        {
            StatusText.Text = "已取消";
        }
        catch (Exception ex)
        {
            StatusText.Text = "登录失败：" + ex.Message;
        }
    }

    private void OpenLink_Click(object sender, RoutedEventArgs e)
    {
        var url = _deviceCode?.VerificationUri ?? "https://www.microsoft.com/link";
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch
        {
            // The URL is also shown in the window.
        }
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        _cts.Cancel();
        DialogResult = false;
        Close();
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Cancel_Click(sender, e);

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        try { DragMove(); } catch (InvalidOperationException) { }
    }
}
