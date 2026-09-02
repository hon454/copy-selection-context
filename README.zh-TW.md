# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**[English](README.md)** · **[한국어](README.ko.md)** · **[简体中文](README.zh-CN.md)** · **繁體中文** · **[日本語](README.ja.md)**

> 使用一個快速鍵將檔案路徑、行號和程式碼複製到剪貼簿——採用可直接貼到 AI 助理的格式。

向 Claude、ChatGPT 等 AI 程式設計助理提供程式碼上下文時，還在手動輸入檔案路徑和行號嗎？**Copy Selection Context** 只需一個快速鍵，即可將 `@path#Lline` 格式的上下文複製到剪貼簿。

[![Get from JetBrains Marketplace](https://img.shields.io/badge/Get%20from-JetBrains%20Marketplace-000000?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)

## 功能

- **快速鍵複製** — 按下 `Ctrl+Alt+C`，立即複製檔案路徑和行號
- **相對或絕對路徑** — 可選擇專案相對路徑或絕對路徑
- **彈性的輸出格式** — 支援 Claude Code 參照、Path:Line 輸出或自訂範本
- **包含程式碼內容** — 可選擇將所選程式碼以 Markdown 程式碼區塊形式包含在內
- **複製歷史記錄** — 按下 `Ctrl+Alt+H` 瀏覽最近的複製歷史記錄
- **GitHub/GitLab 永久連結** — 複製所選行對應的 Git 永久連結
- **複製回饋** — 標示已複製的行、顯示可選通知，並在狀態列保留最近一次複製內容
- **多 caret 上下文** — 分別格式化每個 caret，並以空行分隔其路徑/程式碼區塊
- **準確的選取結束位置** — 使用 IntelliJ 的 `selectionEnd - 1` 作為最後納入的 offset，避免多算尾隨行
- **本地化設定** — 使用本地化輸出格式標籤，並保持英文/韓文資源鍵一致
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

主要操作與個別路徑/程式碼操作會使用設定中選擇的輸出格式。路徑統一使用正斜線。

每個 caret 都會產生自己的從 1 開始的包含範圍與可選程式碼區塊，各區塊之間以空行分隔。IntelliJ 的選取結束位置是 exclusive，因此由 `selectionEnd - 1` 決定最後納入的行；在下一行起點結束的選取不會包含該行。

**Claude Code (`claude`，預設)**：
- 單行：` @src/main/kotlin/App.kt#L42 `
- 多行：` @src/main/kotlin/App.kt#L250-253 `

**Path:Line (`pathline`)**：
- 單行：`src/main/kotlin/App.kt:42`
- 多行：`src/main/kotlin/App.kt:250-253`

**自訂範本 (`template`)**：
- 可從 Path and Range、Claude Reference 或 With Code Block 預設開始，也可以自行輸入範本
- 可用變數：`{path}`、`{line}`、`{range}`、`{code}`、`{lang}` 與 `{filename}`
- 設定畫面會預覽結果並提示未知變數

**包含程式碼內容**（Claude Code 與 Path:Line 會附加程式碼區塊；自訂範本透過 `{code}` 放置內容）：
````
 @src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

個別的 **Copy GitHub/GitLab Permalink** 操作會在背景執行緒讀取一般儲存庫或 linked worktree 中繼資料，並為每個 caret 產生固定到提交的 URL。只有最新請求可以更新剪貼簿。若無法解析遠端位址或提交，操作會顯示錯誤並保持剪貼簿不變。

### 歷史記錄、通知與狀態

- 標準路徑/程式碼複製操作會將項目加入專案專屬歷史記錄。按下 `Ctrl+Alt+H` 可開啟已儲存項目，選取後會再次複製完整內容。
- 複製通知預設啟用，也可以關閉。標準路徑/程式碼複製與 Git 永久連結成功後會顯示通知。
- 標準複製會替換作用中編輯器的邊欄標記，並在狀態列小工具顯示含前綴最多 40 個字元的單行、Unicode-safe、markup-escaped 預覽。按一下小工具可再次複製最後一次的完整內容。
- 可選的本機使用分析會在 IDE 應用程式設定中記錄複製次數與輸出格式使用量。預設關閉，且不會將資料傳送到裝置之外。

### 設定

`Settings` → `Tools` → `Copy Selection Context`：

- **Path type** — Absolute（預設）或 Relative
- **Output format** — Claude Code（預設）、Path:Line 或 Custom Template
- **Custom format template** — 選擇預設，或使用具無障礙標籤與變數驗證的六行多行編輯器，以及可聚焦的六行即時預覽
- **Include code content** — 包含所選程式碼；未選取時包含目前行（預設關閉）
- **Trim code whitespace** — 移除所含程式碼首尾的空白（預設關閉）
- **Show copy notifications** — 在支援的複製操作後顯示氣泡通知（預設開啟）
- **Copy history size** — 每個專案保留 0–100 筆記錄（預設：10）；設為 `0` 會停用並清空歷史記錄
- **Local usage analytics** — 僅在本機儲存選擇啟用的複製計數（預設關閉）

複製歷史記錄可能包含已複製的程式碼。資料僅儲存在 IDE 的本機、非漫遊工作區中，不會寫入可共享的專案設定。使用歷史記錄彈出視窗底部的 **Clear all history** 可移除所有項目。縮小最大筆數時，較舊的項目會立即移除；先前儲存在 `copySelectionHistory.xml` 的歷史記錄會遷移至本機工作區儲存空間，之後 IDE 會清理舊檔案。

#### 設定畫面

![Copy Selection Context 設定畫面](docs/images/settings-copy-selection-context.png)

可在同一個畫面中設定路徑類型、輸出與多行範本、程式碼處理、通知、歷史記錄和本機分析。

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

測試套件包含 `CopySelectionActionFixtureTest`，用於驗證真實 IntelliJ 編輯器/操作/剪貼簿與非同步永久連結流程。CI 會在發佈構件前執行完整測試、專案與結構檢查、`verifyPlugin` 相容性驗證、外掛程式打包、ZIP 檢查並上傳診斷資訊。

## 支援

如果這個外掛程式對你有幫助，歡迎請我喝杯咖啡！

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## 授權條款

本專案採用 Apache License 2.0 授權，詳情請參閱 [LICENSE](LICENSE)。

## 作者

由 [@hon454](https://github.com/hon454) 開發
