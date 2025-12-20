

<div align="center">

![FakeLauncher Logo](ic_launcher_round.png)
# FakeLauncher ⌚️
**一款将 Wear OS 智能手表伪装为轻智能手表的应用程序。**

[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.0-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/MIT-Licence-green.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Wear%20OS-important)
[![Min API](https://img.shields.io/badge/API-30%2B-brightgreen)](https://developer.android.com/about/versions/11)

*为您的摸鱼作保障。*

</div>

## ✨ 项目简介

**FakeLauncher** 是一款运行在 Wear OS 智能手表上的创新应用。它的核心功能是呈现一个极度精简、专注于基础信息的用户界面（如时间、日期、步数），使其在外观和即时交互上接近一款轻智能手表（如米动手表、华为手环的简单表盘界面）。

> **请注意**：本项目为技术演示与概念验证，旨在探索 Wear OS 的界面定制能力与系统交互边界。请遵守您所在地的法律法规以及设备的使用条款。


## 🚀 主要特性

- **极简伪装表盘**：常亮显示清晰的时间、日期和基本健康数据，模仿轻智能手表的主界面。
- **可控应用抽屉**：通过上滑手势呼出，仅显示预先设定的少数“白名单”应用，隐藏复杂的智能功能。
- **沉浸式体验**：全屏悬浮窗实现，尽可能覆盖系统原生界面元素。
- **低功耗优化**：界面设计以节省电量为首要目标，减少不必要的动画和刷新。
- **手势导航**：通过自然的上下、左右滑动手势在表盘与应用界面间切换。

## 🛠️ 技术栈

- **语言**： [Kotlin](https://kotlinlang.org/)
- **UI 框架**： [Jetpack Compose for Wear OS](https://developer.android.com/jetpack/compose/wear)
- **目标 API**： Wear OS 3.0 (API 30) 及以上
- **架构**： 基于 Activity 与 Compose 的混合架构

## 📦 开始使用

### 前提条件
1.  一款已启用开发者选项和 ADB 调试的 Wear OS 智能手表。
2.  Android Studio Flamingo 或更高版本。
3.  手表已通过 Wi-Fi 或 USB 与开发机连接。

### 安装与运行
1.  **克隆仓库**
    ```bash
    git clone https://github.com/your-username/FakeLauncher.git
    cd FakeLauncher
    ```
2.  **用 Android Studio 打开项目**
    打开 Android Studio，选择 `Open`，然后导航到克隆的 `FakeLauncher` 文件夹。
3.  **连接设备**
    确保您的手表出现在 Android Studio 顶部的设备选择器中。
4.  **构建并运行**
    点击运行按钮 (▶️) 或使用快捷键 `Shift+F10`。应用将自动编译并安装到手表中。

### 权限授予
首次运行，应用会引导您开启 **“悬浮窗权限”**。这是应用覆盖系统界面所必需的。请务必在系统设置中允许此权限。

## 🤝 参与贡献

我们欢迎并感谢所有的贡献！请随时通过以下方式参与：
1.  **提交 Issue**：报告错误、请求新功能或提出改进建议。
2.  **发起 Pull Request**：
    - Fork 本仓库。
    - 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)。
    - 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)。
    - 推送到分支 (`git push origin feature/AmazingFeature`)。
    - 打开一个 Pull Request。

请确保您的代码遵循项目现有的 Kotlin 风格。

## 📄 许可证

本项目采用 **MIT License** 许可证。
详情请参阅 [LICENSE](LICENSE) 文件。

---

<div align="center">
如果这个项目对您有帮助，请考虑给它一个 ⭐️ <strong>Star</strong>！
</div>

---

**免责声明**：此项目为开源技术项目。开发者不对任何因使用此软件而导致的问题负责，包括但不限于设备故障、数据丢失或违反学校、工作场所规定所带来的后果。请用户自行评估风险并负责任地使用。
