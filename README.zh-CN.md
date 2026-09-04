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
- **GitHub/GitLab 永久链接** — 复制所选行的 Git 永久链接，并获得与标准结果相同的本地历史和复制反馈
- **复制反馈** — 标记已复制的行、显示可选通知，并在状态栏保留最后一次复制内容
- **尊重用户的评价入口** — 在充分使用后，每个版本最多显示一次诚实评价请求，并提供被动 Marketplace 链接
- **多 caret 上下文** — 分别格式化每个 caret，并以空行分隔其路径/代码块
- **准确的选择结束位置** — 使用 IntelliJ 的 `selectionEnd - 1` 作为最后包含的 offset，避免多算尾随行
- **本地化 IDE 界面** — 操作、设置、通知、历史、状态和格式标签支持英语、韩语、日语、简体中文和繁体中文
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

## 验证发布下载

由 `.github/workflows/release.yml` 附加的插件 ZIP 是规范发布产物。配置签名凭据时，规范产物是已验证签名的 `-signed.zip`，同一文件会原样发布到 JetBrains Marketplace。未配置签名凭据时，工作流会明确将规范 ZIP 标记为未签名，并跳过 Marketplace 发布。IntelliJ Platform Gradle Plugin 会把构建 JVM 和操作系统写入 `META-INF/MANIFEST.MF`，因此本地构建即使功能相同，在不同环境中也不一定逐字节一致。为此，每个 GitHub Release 都包含针对实际发布 ZIP 的 `SHA256SUMS` 和 GitHub artifact attestation。

下载这两个资产，并在 Linux 上验证校验和：

```bash
gh release download v1.2.0 \
  --repo hon454/copy-selection-context \
  --pattern '*.zip' \
  --pattern SHA256SUMS
sha256sum --check SHA256SUMS
```

在 macOS 上，请改用标准的 `shasum` 命令：

```bash
shasum -a 256 --check SHA256SUMS
```

然后验证 GitHub 是否证明同一个 ZIP 来自此仓库的规范发布工作流和标签。请使用实际下载的文件名；已签名发布带有如下所示的 `-signed.zip` 后缀，明确未签名的发布则没有该后缀（需要时替换版本）：

```bash
gh attestation verify copy-selection-context-1.2.0-signed.zip \
  --repo hon454/copy-selection-context \
  --signer-workflow hon454/copy-selection-context/.github/workflows/release.yml \
  --source-ref refs/tags/v1.2.0
```

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

单独的 **Copy GitHub/GitLab Permalink** 操作会在后台线程读取普通仓库或 linked worktree 元数据，并为每个 caret 生成固定到提交的 URL。只有最新的标准复制或永久链接请求可以发布结果，较早完成的异步任务无法覆盖较新的复制。如果无法解析远程地址或提交，它会报告错误并保持剪贴板不变。

### 历史、通知与状态

- 成功的标准路径/代码复制和 Git 永久链接复制都会将条目添加到项目专属历史的开头。按下 `Ctrl+Alt+H` 可打开已存储的条目，选择条目会再次复制其完整内容。
- 复制通知默认启用，也可以关闭。标准路径/代码复制和 Git 永久链接复制后会显示通知。
- 只有在已启用通知且受支持的 UI 环境中执行的标准复制才会增加评价计数器；通知关闭、测试、headless 和不受支持的环境都不会改变计数器或提示状态。在一个 IDE 会话中的第 10 次有效复制可为当前插件版本显示一次非模态的诚实评价请求。**Review on Marketplace** 会打开官方 [Marketplace 评价页面](https://plugins.jetbrains.com/plugin/30262-copy-selection-context/reviews)，**Later** 会跳过当前版本后续请求，**Don't ask again** 会永久禁止提示。通知关闭时，只提供设置中的被动链接。
- 精确的会话次数不会持久化。仅在本地非漫游的 `copySelectionReview.xml` 中保存 `lastPromptedVersion`、永久禁止选择及是否打开过 Marketplace 页面；不会涉及复制内容、文件数据、分析、评价结果、遥测或自动网络请求。
- 成功的标准复制或 Git 永久链接复制会替换活动编辑器中的边栏标记，并在状态栏小组件中显示包含前缀在内最多 40 个字符的单行、Unicode-safe、markup-escaped 预览。单击小组件可再次复制插件最近一次成功复制的完整内容。永久链接不会增加本地分析或评价提示计数。
- 可选的本地使用分析会按输出格式和检测到的文件语言统计成功的标准复制操作。可在设置中查看所有计数器的不可变快照，并在确认后重置。此功能默认关闭，数据只存储在本地 IDE 应用设置中，绝不会传输。

### 设置

`Settings` → `Tools` → `Copy Selection Context`：

- **Path type** — Absolute（默认）或 Relative
- **Output format** — Claude Code（默认）、Path:Line 或 Custom Template
- **Custom format template** — 选择预设，或使用带无障碍标签与变量验证的六行多行编辑器，以及可聚焦的六行实时预览
- **Include code content** — 包含所选代码；未选择时包含当前行（默认关闭）
- **Trim code whitespace** — 删除所含代码首尾的空白（默认关闭）
- **Show copy notifications** — 在支持的复制操作后显示气泡通知（默认开启）
- **Review on Marketplace** — 手动打开官方评价页面，不会强制显示提示
- **Copy history size** — 每个项目保留 0–100 条记录（默认：10）；设为 `0` 会禁用并清空历史
- **Local usage analytics** — 查看和重置仅存储在本机的可选总计、输出格式和语言计数器（默认关闭；绝不传输）

复制历史可能包含已复制的代码。数据仅存储在 IDE 的本地非漫游工作区中，不会写入可共享的项目设置。每条已存储记录的 UTF-8 内容上限为 256 KiB，每个项目的历史总量上限为 2 MiB。超过 256 KiB 的结果仍会完整复制，但不会添加到历史中。当配置的记录数或总字节限额超出时，最旧的记录会立即删除。可使用历史弹窗底部的 **Clear all history** 删除所有记录。现有历史（包括从 `copySelectionHistory.xml` 迁移的数据）在加载时也会按相同限额规范化，之后 IDE 会清理旧文件。

#### 设置界面

![Copy Selection Context 设置界面](docs/images/settings-copy-selection-context.png)

可在一个界面中配置路径类型、输出与模板、代码处理、通知、评价入口、历史记录和本地分析。

### 会话上下文集合（尚未发布）

在编辑器的 Copy Selection Context 子菜单或 Find Action 中运行 **添加到上下文集合**，即可收集多个文件的选择内容。每个光标捕获其选择范围或当前行，包括尚未保存的文本，且不改变剪贴板或编辑器焦点。此操作没有默认快捷键，可在 Keymap 中分配。

代码、路径、文件名、语言、行范围、捕获编号和时间均固定为捕获时的值。未更改的重复捕获会跳过；代码改变后会新增独立快照。仅切换相对或绝对路径设置仍视为重复，并保留原显示路径。重命名和删除状态与捕获内容分开跟踪。

项目会话最多保留 100 项，每项原始 UTF-8 代码上限为 256 KiB，总量上限为 2 MiB。超限的多光标批次会整体拒绝，不截断内容，也不自动淘汰旧项。集合及其独立且默认开启的代码包含选项不会持久化，在项目关闭或插件卸载时丢弃。收集不改变现有复制历史、状态栏、评价计数或统计。操作系统及外部剪贴板历史遵循各自策略。

无需活动编辑器即可从子菜单或 Find Action 执行 **复制整个上下文集合**，没有默认快捷键。当前格式、模板和空白修剪设置应用于已捕获的路径和代码。内置格式为同一位置的快照添加固定捕获编号和 UTC 时间，自定义模板保留原有替换语义。任何项目输出为空白都会阻止复制。输出超过 256 KiB 时需确认，超过 4 MiB 时禁止复制；快照、引用和大小警告在同一对话框确认。复制后保留集合，不添加历史或 gutter 标记，并遵循通知设置、可选统计和独立评价条件。跨项目以最新插件复制请求为准，包括历史和状态栏重新复制；原生 Copy 及外部剪贴板历史不在此顺序控制范围内。参见 [#75](https://github.com/hon454/copy-selection-context/issues/75) 的[输出契约](docs/development/context-collection-output-contract.md)和[示例选择内容](docs/samples/context-collection/README.md)。管理工具窗口及真实截图将在 [#74](https://github.com/hon454/copy-selection-context/issues/74) 中实现。

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
./gradlew allTests       # Run the complete test suite

# Windows
gradlew.bat buildPlugin
gradlew.bat runIde
gradlew.bat allTests
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
