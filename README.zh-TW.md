# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**[English](README.md)** · **[한국어](README.ko.md)** · **[简体中文](README.zh-CN.md)** · **繁體中文** · **[日本語](README.ja.md)**

> 使用一個快速鍵將檔案路徑、行號和程式碼複製到剪貼簿——採用可直接貼到 AI 助理的格式。

向 Claude、ChatGPT 等 AI 程式設計助理提供程式碼上下文時，還在手動輸入檔案路徑和行號嗎？**Copy Selection Context** 只需一個快速鍵，即可將 `@path#Lline` 格式的上下文複製到剪貼簿。

## 功能

- **快速鍵複製** — 按下 `Ctrl+Alt+C`，立即複製檔案路徑和行號
- **相對或絕對路徑** — 可選擇專案相對路徑或絕對路徑
- **包含程式碼內容** — 可選擇將所選程式碼以 Markdown 程式碼區塊形式包含在內
- **複製歷史記錄** — 按下 `Ctrl+Alt+H` 瀏覽最近的複製歷史記錄
- **GitHub/GitLab 永久連結** — 複製所選行對應的 Git 永久連結
- **智慧行號處理** — 未選取文字時複製游標所在行的行號
- **內容選單** — 可從編輯器右鍵選單存取所有操作
- **跨平台** — 支援 Windows、macOS 和 Linux

## 安裝

### 從 JetBrains Marketplace 安裝

1. `File` → `Settings` → `Plugins`
2. 搜尋 **“Copy Selection Context”**
3. 按一下 `Install`

### 從磁碟安裝

1. 從 [Releases](https://github.com/hon454/copy-selection-context/releases) 頁面下載最新的 `.zip` 檔案
2. `File` → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. 選取下載的 `.zip` 檔案 → 重新啟動 IDE

## 使用方式

### 鍵盤快速鍵

| 操作 | Windows/Linux | macOS |
|------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+C` | `Cmd+Alt+C` |
| Show Copy History | `Ctrl+Alt+H` | `Ctrl+Alt+H` |

> 可在 `Settings` → `Keymap` 中自訂快速鍵。

### 內容選單

在編輯器中按一下滑鼠右鍵 → **Copy Selection Context** 子選單：

| 操作 | 說明 |
|------|------|
| Copy Selection Context | 根據設定複製路徑和行號（主要操作） |
| Copy Relative Path with Line Numbers | 使用專案相對路徑複製 |
| Copy Absolute Path with Line Numbers | 使用絕對路徑複製 |
| Copy with Code Content | 複製路徑、行號和程式碼區塊 |
| Copy GitHub/GitLab Permalink | 複製 Git 遠端儲存庫永久連結 |
| Show Copy History | 顯示最近的複製歷史記錄彈出視窗 |

### 輸出格式

輸出為 `@path#Lline` 格式，可直接貼到 AI 助理中。

**僅路徑（預設）**：
- 單行：`@src/main/kotlin/App.kt#L42`
- 多行：`@src/main/kotlin/App.kt#L250-253`

**包含程式碼內容（在設定中啟用）**：
````
@src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

### 設定

`Settings` → `Tools` → `Copy Selection Context`：

- **Path type** — Absolute（預設）或 Relative
- **Include code content** — 是否包含程式碼區塊

#### 設定畫面

![Copy Selection Context 設定畫面](docs/images/settings-copy-selection-context.png)

可在同一個畫面中設定路徑類型、輸出格式、是否包含程式碼、通知行為和歷史記錄選項。

## 相容的 IDE

支援所有基於 IntelliJ Platform 2024.3+ 的 IDE：

IntelliJ IDEA · Android Studio · PyCharm · WebStorm · PhpStorm · CLion · GoLand · Rider · RubyMine

## 開發

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

有關開發和發佈的詳細指南，請參閱 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 支援

如果這個外掛程式對你有幫助，歡迎請我喝杯咖啡！

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## 授權條款

本專案採用 Apache License 2.0 授權，詳情請參閱 [LICENSE](LICENSE)。

## 作者

由 [@hon454](https://github.com/hon454) 開發
