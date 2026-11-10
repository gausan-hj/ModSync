using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Win32;
using ModSyncLauncher.Services;

namespace ModSyncLauncher;

public partial class AccountWindow : Window
{
    private readonly AccountProfile _profile;
    private string? _avatarPath;

    public AccountProfile Profile => _profile;

    public AccountWindow(AccountProfile profile)
    {
        InitializeComponent();
        _profile = new AccountProfile
        {
            Name = profile.Name,
            Type = profile.Type,
            AvatarPath = profile.AvatarPath,
            Uuid = profile.Uuid,
            AccessToken = profile.AccessToken,
            RefreshToken = profile.RefreshToken,
            ExpiresAt = profile.ExpiresAt,
            SkinUrl = profile.SkinUrl,
        };
        _avatarPath = profile.AvatarPath;
        NameBox.Text = profile.Name;
        TypeBox.SelectedIndex = profile.Type == "微软账号" ? 1 : 0;
        SetPreview(AccountStore.LoadAvatar(_avatarPath));
        if (TypeBox.SelectedIndex == 1 && string.IsNullOrWhiteSpace(_profile.AccessToken))
        {
            Loaded += async (_, _) =>
            {
                await Task.Delay(250);
                MicrosoftLogin_Click(this, new RoutedEventArgs());
            };
        }
    }

    private void SetPreview(BitmapImage? image)
    {
        if (image != null)
        {
            AvatarPreview.Background = new ImageBrush(image) { Stretch = Stretch.UniformToFill };
            return;
        }
        AvatarPreview.Background = new ImageBrush(new BitmapImage(
            new Uri("pack://application:,,,/Assets/avatar.png"))) { Stretch = Stretch.UniformToFill };
    }

    private void ChooseAvatar_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog
        {
            Title = "选择头像",
            Filter = "图片|*.png;*.jpg;*.jpeg;*.webp|所有文件|*.*",
        };
        if (dialog.ShowDialog(this) != true)
        {
            return;
        }
        _avatarPath = dialog.FileName;
        SetPreview(AccountStore.LoadAvatar(_avatarPath));
    }

    private void TypeBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || TypeBox.SelectedIndex != 1) return;
        if (!string.IsNullOrWhiteSpace(_profile.AccessToken)) return;
        Dispatcher.BeginInvoke(new Action(() => MicrosoftLogin_Click(this, new RoutedEventArgs())));
    }

    private void MicrosoftLogin_Click(object sender, RoutedEventArgs e)
    {
        var login = new MicrosoftLoginWindow { Owner = this };
        if (login.ShowDialog() != true || login.Result == null) return;
        var result = login.Result;
        _profile.Name = result.Name;
        _profile.Type = "微软账号";
        _profile.Uuid = result.Uuid;
        _profile.AccessToken = result.AccessToken;
        _profile.RefreshToken = result.RefreshToken;
        _profile.ExpiresAt = result.ExpiresAt;
        _profile.AvatarPath = result.AvatarPath;
        _avatarPath = result.AvatarPath;
        NameBox.Text = result.Name;
        TypeBox.SelectedIndex = 1;
        SetPreview(AccountStore.LoadAvatar(_avatarPath));
        AccountStore.Save(_profile);
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        var name = NameBox.Text.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            MessageBox.Show(this, "用户名不能为空。", "账号", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        if (TypeBox.SelectedIndex == 1 && string.IsNullOrWhiteSpace(_profile.AccessToken))
        {
            MessageBox.Show(this, "请先点击“使用微软账号登录…”完成验证。", "账号", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        _profile.Name = name;
        _profile.Type = TypeBox.SelectedIndex == 1 ? "微软账号" : "离线账号";
        _profile.AvatarPath = _avatarPath;
        AccountStore.Save(_profile);
        DialogResult = true;
        Close();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        try { DragMove(); } catch (InvalidOperationException) { }
    }
}
