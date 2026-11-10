using System.Windows;
using System.Windows.Controls;

namespace ModSyncLauncher.Controls;

/// <summary>
/// PCL MyCard 的最小移植：白色圆角卡片、标题区、固定内容边距。
/// 尺寸和边距按 PCL 的 MyCard 结构还原。
/// </summary>
public class PclCard : HeaderedContentControl
{
    static PclCard()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(PclCard), new FrameworkPropertyMetadata(typeof(PclCard)));
    }
}
