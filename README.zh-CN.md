# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**[English](README.md)** · **[한국어](README.ko.md)** · **简体中文** · **[繁體中文](README.zh-TW.md)** · **[日本語](README.ja.md)**

> 使用一个快捷键将文件路径、行号和代码复制到剪贴板——采用可直接粘贴到 AI 助手的格式。

向 Claude、ChatGPT 等 AI 编程助手提供代码上下文时，还在手动输入文件路径和行号吗？**Copy Selection Context** 只需一个快捷键，即可将 `@path#Lline` 格式的上下文复制到剪贴板。

[![Get from JetBrains Marketplace](https://img.shields.io/badge/Get%20from-JetBrains%20Marketplace-000000?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)

## 功能

- **快捷键复制** — 按下 `Ctrl+Alt+C`，立即复制文件路径和行号
- **相对或绝对路径** — 可选择项目相对路径或绝对路径
- **灵活的输出格式** — 支持 Claude Code 引用、Path:Line 输出或自定义模板
- **包含代码内容** — 可选择将所选代码以 Markdown 代码块形式包含在内
- **复制历史** — 按下 `Ctrl+Alt+H` 浏览最近的复制历史
- **GitHub/GitLab 永久链接** — 复制所选行对应的 Git 永久链接
- **复制反馈** — 标记已复制的行、显示可选通知，并在状态栏保留最后一次复制内容
- **多 caret 上下文** — 分别格式化每个 caret，并以空行分隔其路径/代码块
- **准确的选择结束位置** — 使用 IntelliJ 的 `selectionEnd - 1` 作为最后包含的 offset，避免多算尾随行
- **本地化设置** — 使用本地化输出格式标签，并保持英语/韩语资源键一致
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

主要操作和单独的路径/代码操作使用设置中选择的输出格式。路径统一使用正斜杠。

每个 caret 都会生成自己的从 1 开始的包含范围和可选代码块，各块之间以空行分隔。IntelliJ 的选择结束位置是 exclusive，因此由 `selectionEnd - 1` 决定最后包含的行；在下一行起点结束的选择不会包含该行。

**Claude Code (`claude`，默认)**：
- 单行：` @src/main/kotlin/App.kt#L42 `
- 多行：` @src/main/kotlin/App.kt#L250-253 `

**Path:Line (`pathline`)**：
- 单行：`src/main/kotlin/App.kt:42`
- 多行：`src/main/kotlin/App.kt:250-253`

**自定义模板 (`template`)**：
- 可从 Path and Range、Claude Reference 或 With Code Block 预设开始，也可以自行输入模板
- 标准复制操作会填充的变量：`{path}`、`{line}`、`{range}`、`{code}`、`{lang}` 和 `{filename}`
- 设置界面会预览结果并提示未知变量

**包含代码内容**（Claude Code 和 Path:Line 会附加代码块；自定义模板通过 `{code}` 放置内容）：
````
 @src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

单独的 **Copy GitHub/GitLab Permalink** 操作会在后台线程读取普通仓库或 linked worktree 元数据，并为每个 caret 生成固定到提交的 URL。只有最新请求可以更新剪贴板。如果无法解析远程地址或提交，它会报告错误并保持剪贴板不变。

### 历史、通知与状态

- 标准路径/代码复制操作会将条目添加到项目专属历史的开头。按下 `Ctrl+Alt+H` 可打开已存储的条目，选择条目会再次复制其完整内容。
- 复制通知默认启用，也可以关闭。标准路径/代码复制和 Git 永久链接复制后会显示通知。
- 标准复制会替换活动编辑器中的边栏标记，并在状态栏小组件中显示包含前缀在内最多 40 个字符的单行、Unicode-safe、markup-escaped 预览。单击小组件可再次复制最后一次的完整内容。
- 可选的本地使用分析会在 IDE 应用设置中记录复制次数和输出格式使用量。默认关闭，且不会将数据发送到设备之外。

### 设置

`Settings` → `Tools` → `Copy Selection Context`：

- **Path type** — Absolute（默认）或 Relative
- **Output format** — Claude Code（默认）、Path:Line 或 Custom Template
- **Custom format template** — 选择预设，或使用带无障碍标签与变量验证的六行多行编辑器，以及可聚焦的六行实时预览
- **Include code content** — 包含所选代码；未选择时包含当前行（默认关闭）
- **Trim code whitespace** — 删除所含代码首尾的空白（默认关闭）
- **Show copy notifications** — 在支持的复制操作后显示气泡通知（默认开启）
- **Copy history size** — 每个项目保留 0–100 条记录（默认：10）；设为 `0` 会禁用并清空历史
- **Local usage analytics** — 仅在本机保存选择启用的复制计数（默认关闭）

复制历史可能包含已复制的代码。数据仅存储在 IDE 的本地非漫游工作区中，不会写入可共享的项目设置。每条已存储记录的 UTF-8 内容上限为 256 KiB，每个项目的历史总量上限为 2 MiB。超过 256 KiB 的结果仍会完整复制，但不会添加到历史中。当配置的记录数或总字节限额超出时，最旧的记录会立即删除。可使用历史弹窗底部的 **Clear all history** 删除所有记录。现有历史（包括从 `copySelectionHistory.xml` 迁移的数据）在加载时也会按相同限额规范化，之后 IDE 会清理旧文件。

#### 设置界面

![Copy Selection Context 设置界面](docs/images/settings-copy-selection-context.png)

可在一个界面中配置路径类型、输出与模板、代码处理、通知、历史记录和本地分析。

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

测试套件包含 `CopySelectionActionFixtureTest`，用于验证真实 IntelliJ 编辑器/操作/剪贴板和异步永久链接流程。CI 会在发布构件前运行完整测试、项目与结构检查、`verifyPlugin` 兼容性验证、插件打包、ZIP 检查并上传诊断信息。

## 支持

如果这个插件对你有帮助，欢迎请我喝杯咖啡！

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## 许可证

本项目采用 Apache License 2.0，详情请参阅 [LICENSE](LICENSE)。

## 作者

由 [@hon454](https://github.com/hon454) 开发
