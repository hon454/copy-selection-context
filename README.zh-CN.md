# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**[English](README.md)** · **[한국어](README.ko.md)** · **简体中文** · **[繁體中文](README.zh-TW.md)** · **[日本語](README.ja.md)**

> 使用一个快捷键将文件路径、行号和代码复制到剪贴板——采用可直接粘贴到 AI 助手的格式。

向 Claude、ChatGPT 等 AI 编程助手提供代码上下文时，还在手动输入文件路径和行号吗？**Copy Selection Context** 只需一个快捷键，即可将 `@path#Lline` 格式的上下文复制到剪贴板。

## 功能

- **快捷键复制** — 按下 `Ctrl+Alt+C`，立即复制文件路径和行号
- **相对或绝对路径** — 可选择项目相对路径或绝对路径
- **包含代码内容** — 可选择将所选代码以 Markdown 代码块形式包含在内
- **复制历史** — 按下 `Ctrl+Alt+H` 浏览最近的复制历史
- **GitHub/GitLab 永久链接** — 复制所选行对应的 Git 永久链接
- **智能行号处理** — 未选择文本时复制光标所在行的行号
- **上下文菜单** — 可从编辑器右键菜单访问所有操作
- **跨平台** — 支持 Windows、macOS 和 Linux

## 安装

### 从 JetBrains Marketplace 安装

1. `File` → `Settings` → `Plugins`
2. 搜索 **“Copy Selection Context”**
3. 点击 `Install`

### 从磁盘安装

1. 从 [Releases](https://github.com/hon454/copy-selection-context/releases) 页面下载最新的 `.zip` 文件
2. `File` → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. 选择下载的 `.zip` 文件 → 重启 IDE

## 使用方法

### 键盘快捷键

| 操作 | Windows/Linux | macOS |
|------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+C` | `Cmd+Alt+C` |
| Show Copy History | `Ctrl+Alt+H` | `Ctrl+Alt+H` |

> 可在 `Settings` → `Keymap` 中自定义快捷键。

### 上下文菜单

在编辑器中右键单击 → **Copy Selection Context** 子菜单：

| 操作 | 说明 |
|------|------|
| Copy Selection Context | 根据设置复制路径和行号（主要操作） |
| Copy Relative Path with Line Numbers | 使用项目相对路径复制 |
| Copy Absolute Path with Line Numbers | 使用绝对路径复制 |
| Copy with Code Content | 复制路径、行号和代码块 |
| Copy GitHub/GitLab Permalink | 复制 Git 远程仓库永久链接 |
| Show Copy History | 显示最近的复制历史弹窗 |

### 输出格式

输出为 `@path#Lline` 格式，可直接粘贴到 AI 助手中。

**仅路径（默认）**：
- 单行：`@src/main/kotlin/App.kt#L42`
- 多行：`@src/main/kotlin/App.kt#L250-253`

**包含代码内容（在设置中启用）**：
````
@src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

### 设置

`Settings` → `Tools` → `Copy Selection Context`：

- **Path type** — Absolute（默认）或 Relative
- **Include code content** — 是否包含代码块
- **Copy history size** — 最多保留 100 条记录，或设为 `0` 以禁用历史记录

复制历史可能包含已复制的代码。数据仅存储在 IDE 的本地非漫游工作区中，不会写入可共享的项目设置。可使用历史弹窗底部的 **Clear all history** 删除所有记录。减小最大数量时，较旧的记录会立即删除；先前存储在 `copySelectionHistory.xml` 中的历史会迁移到本地工作区存储，旧文件随后由 IDE 清理。

#### 设置界面

![Copy Selection Context 设置界面](docs/images/settings-copy-selection-context.png)

可在一个界面中配置路径类型、输出格式、是否包含代码、通知行为和历史记录选项。

## 兼容的 IDE

支持所有基于 IntelliJ Platform 2024.3+ 的 IDE：

IntelliJ IDEA · Android Studio · PyCharm · WebStorm · PhpStorm · CLion · GoLand · Rider · RubyMine

## 开发

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

# Unix / macOS
./gradlew buildPlugin    # Build plugin ZIP
./gradlew runIde         # Run dev IDE with plugin
./gradlew test           # Run tests

# Windows
gradlew.bat buildPlugin
gradlew.bat runIde
gradlew.bat test
```

有关开发和发布的详细指南，请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 支持

如果这个插件对你有帮助，欢迎请我喝杯咖啡！

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## 许可证

本项目采用 Apache License 2.0，详情请参阅 [LICENSE](LICENSE)。

## 作者

由 [@hon454](https://github.com/hon454) 开发
