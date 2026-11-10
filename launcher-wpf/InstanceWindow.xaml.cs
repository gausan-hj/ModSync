using System.Windows;
using System.Windows.Input;

namespace ModSyncLauncher;

public partial class InstanceWindow : Window
{
    public string InstanceName => NameBox.Text.Trim();
    public string VersionId => VersionBox.SelectedItem as string ?? "";
    public string LoaderId => (LoaderBox.SelectedItem as System.Windows.Controls.ComboBoxItem)?.Content?.ToString()?.ToLowerInvariant() ?? "neoforge";

    public InstanceWindow(IEnumerable<string> versions, string currentVersion, string defaultName)
    {
        InitializeComponent();
        foreach (var version in versions) VersionBox.Items.Add(version);
        VersionBox.SelectedItem = versions.Contains(currentVersion) ? currentVersion : versions.FirstOrDefault();
        NameBox.Text = defaultName;
        Loaded += (_, _) =>
        {
            NameBox.Focus();
            NameBox.SelectAll();
        };
    }

    private void Create_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(NameBox.Text))
        {
            MessageBox.Show(this, "请输入实例名称。", "新建实例", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        if (VersionBox.SelectedItem == null)
        {
            MessageBox.Show(this, "请选择 Minecraft 版本。", "新建实例", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        DialogResult = true;
        Close();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        try { DragMove(); } catch (InvalidOperationException) { }
    }
}
