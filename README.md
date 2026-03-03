# maimaid

[English](README-en-US.md)

一款专为 maimai DX 玩家打造的现代化原生 iOS 应用。轻松追踪您的分数、分析您的进度并发现新的挑战。

## 功能

- **分数追踪器和评分分析**：根据您的最佳分数实时计算 DX 评分

- **机器学习扫描器**：通过机器学习技术识别分数图片，快速输入分数

- **随机播放器**：老虎机式的随机歌曲选择器，让您在不知道玩什么的时候也能轻松找到心仪的歌曲

- **智能推荐**：基于您当前评分和谱面常量，为您推荐合适的歌曲

- **谱面进度**：可视化您达成特定版本谱面的进度

- **数据同步**：无缝导入 Diving Fish 和 LXNS 中的数据

## 技术栈

- **UI框架**：SwiftUI

- **持久化**：SwiftData

- **机器学习**：YOLOv11 和 CoreML

## 入门指南

### 前提条件

- macOS，Xcode 版本 15+

- iOS 版本 17.0+（用于 SwiftData 和现代 SwiftUI 功能）

### 构建

1. 克隆仓库：

```bash

git clone https://github.com/shikochin/maimaid.git

```

2. 在 Xcode 中打开 `maimaid.xcodeproj` 文件。

3. 选择目标设备/模拟器，然后按 `Cmd+R` 运行。

## 特别鸣谢

- **Diving Fish**：感谢他们提供的宝贵社区数据和 API 支持。

- **LXNS Coffee House**：感谢其提供的歌曲别名和评分 API。

- **maimai**：由 SEGA 开发。所有游戏素材和商标均归其各自所有者所有。

- [**arcade-songs**](https://arcade-songs.zetaraku.dev/)：提供了歌曲数据。

- Google Antigravity.

- Ultralytics Platform 用于模型训练。

- charaDiana 帮助了图像标注。