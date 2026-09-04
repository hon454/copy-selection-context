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
- **GitHub/GitLab 永久連結** — 複製所選行的 Git 永久連結，並提供與標準結果相同的本機歷史記錄與複製回饋
- **複製回饋** — 標示已複製的行、顯示可選通知，並在狀態列保留最近一次複製內容
- **尊重使用者的評論入口** — 充分使用後，每個版本最多顯示一次誠實評論請求，並提供被動 Marketplace 連結
- **多 caret 上下文** — 分別格式化每個 caret，並以空行分隔其路徑/程式碼區塊
- **準確的選取結束位置** — 使用 IntelliJ 的 `selectionEnd - 1` 作為最後納入的 offset，避免多算尾隨行
- **本地化 IDE 介面** — 操作、設定、通知、歷史記錄、狀態和格式標籤支援英文、韓文、日文、簡體中文及繁體中文
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

## 驗證發行下載

由 `.github/workflows/release.yml` 附加的外掛 ZIP 是標準發行成品。設定簽署憑證時，標準成品是已驗證簽章的 `-signed.zip`，同一檔案會原樣發布到 JetBrains Marketplace。未設定簽署憑證時，工作流程會明確將標準 ZIP 標示為未簽署，並略過 Marketplace 發布。IntelliJ Platform Gradle Plugin 會把建置 JVM 和作業系統寫入 `META-INF/MANIFEST.MF`，因此本機建置即使功能相同，在不同環境中也不一定逐位元組一致。為此，每個 GitHub Release 都包含針對實際發布 ZIP 的 `SHA256SUMS` 和 GitHub artifact attestation。

下載這兩個資產，並在 Linux 上驗證 checksum：

```bash
gh release download v1.2.0 \
  --repo hon454/copy-selection-context \
  --pattern '*.zip' \
  --pattern SHA256SUMS
sha256sum --check SHA256SUMS
```

在 macOS 上，請改用標準的 `shasum` 指令：

```bash
shasum -a 256 --check SHA256SUMS
```

接著驗證 GitHub 是否證明同一個 ZIP 來自此儲存庫的標準發行工作流程和標籤。請使用實際下載的檔名；已簽署發行帶有如下所示的 `-signed.zip` 後綴，明確未簽署的發行則沒有該後綴（需要時請替換版本）：

```bash
gh attestation verify copy-selection-context-1.2.0-signed.zip \
  --repo hon454/copy-selection-context \
  --signer-workflow hon454/copy-selection-context/.github/workflows/release.yml \
  --source-ref refs/tags/v1.2.0
```

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

個別的 **Copy GitHub/GitLab Permalink** 操作會在背景執行緒讀取一般儲存庫或 linked worktree 中繼資料，並為每個 caret 產生固定到提交的 URL。只有最新的標準複製或永久連結請求可以發布結果，較早完成的非同步工作無法覆寫較新的複製。若無法解析遠端位址或提交，操作會顯示錯誤並保持剪貼簿不變。

### 歷史記錄、通知與狀態

- 成功的標準路徑/程式碼複製與 Git 永久連結複製都會將項目加入專案專屬歷史記錄。按下 `Ctrl+Alt+H` 可開啟已儲存項目，選取後會再次複製完整內容。
- 複製通知預設啟用，也可以關閉。標準路徑/程式碼複製與 Git 永久連結成功後會顯示通知。
- 只有在已啟用通知且受支援的 UI 環境中執行的標準複製才會增加評論計數器；通知關閉、測試、headless 與不受支援的環境都不會變更計數器或提示狀態。在一個 IDE 工作階段中的第 10 次有效複製可為目前外掛版本顯示一次非模態的誠實評論請求。**Review on Marketplace** 會開啟官方 [Marketplace 評論頁面](https://plugins.jetbrains.com/plugin/30262-copy-selection-context/reviews)，**Later** 會略過目前版本後續請求，**Don't ask again** 會永久停用提示。通知關閉時，只提供設定中的被動連結。
- 精確的工作階段次數不會持久化。僅在本機非漫遊的 `copySelectionReview.xml` 中儲存 `lastPromptedVersion`、永久停用選擇及是否開啟過 Marketplace 頁面；不涉及複製內容、檔案資料、分析、評論結果、遙測或自動網路請求。
- 成功的標準複製或 Git 永久連結複製會替換作用中編輯器的邊欄標記，並在狀態列小工具顯示含前綴最多 40 個字元的單行、Unicode-safe、markup-escaped 預覽。按一下小工具可再次複製外掛最近一次成功複製的完整內容。永久連結不會增加本機分析或評論提示計數。
- 可選的本機使用分析會依輸出格式與偵測到的檔案語言統計成功的標準複製操作。可在設定中查看所有計數器的不可變快照，並於確認後重設。此功能預設關閉，資料只儲存在本機 IDE 應用程式設定中，絕不會傳輸。

### 設定

`Settings` → `Tools` → `Copy Selection Context`：

- **Path type** — Absolute（預設）或 Relative
- **Output format** — Claude Code（預設）、Path:Line 或 Custom Template
- **Custom format template** — 選擇預設，或使用具無障礙標籤與變數驗證的六行多行編輯器，以及可聚焦的六行即時預覽
- **Include code content** — 包含所選程式碼；未選取時包含目前行（預設關閉）
- **Trim code whitespace** — 移除所含程式碼首尾的空白（預設關閉）
- **Show copy notifications** — 在支援的複製操作後顯示氣泡通知（預設開啟）
- **Review on Marketplace** — 手動開啟官方評論頁面，不會強制顯示提示
- **Copy history size** — 每個專案保留 0–100 筆記錄（預設：10）；設為 `0` 會停用並清空歷史記錄
- **Local usage analytics** — 查看並重設僅儲存在本機的選用總計、輸出格式與語言計數器（預設關閉；絕不傳輸）

複製歷史記錄可能包含已複製的程式碼。資料僅儲存在 IDE 的本機、非漫遊工作區中，不會寫入可共享的專案設定。每筆儲存項目的 UTF-8 內容上限為 256 KiB，每個專案的歷史記錄總量上限為 2 MiB。超過 256 KiB 的結果仍會完整複製，但不會加入歷史記錄。當設定的項目數或總位元組限額超出時，會立即從最舊的項目開始移除。使用歷史記錄彈出視窗底部的 **Clear all history** 可移除所有項目。現有歷史記錄（包含從 `copySelectionHistory.xml` 遷移的資料）在載入時也會依相同限額正規化，之後 IDE 會清理舊檔案。

#### 設定畫面

![Copy Selection Context 設定畫面](docs/images/settings-copy-selection-context.png)

可在同一個畫面中設定路徑類型、輸出與多行範本、程式碼處理、通知、評論入口、歷史記錄和本機分析。

### 工作階段上下文集合（尚未發行）

在編輯器的 Copy Selection Context 子選單或 Find Action 執行 **加入上下文集合**，即可收集多個檔案的選取內容。每個游標擷取其選取範圍或目前行，包含尚未儲存的文字，且不改變剪貼簿或編輯器焦點。此動作沒有預設快捷鍵，可在 Keymap 指派。

程式碼、路徑、檔名、語言、行範圍、擷取編號與時間均固定為擷取時的值。未變更的重複擷取會略過；程式碼變更後會新增獨立快照。僅切換相對或絕對路徑設定仍視為重複，並保留原顯示路徑。重新命名和刪除狀態與擷取內容分開追蹤。

專案工作階段最多保留 100 個項目，每項原始 UTF-8 程式碼上限為 256 KiB，總量上限為 2 MiB。超限的多游標批次會整批拒絕，不截斷內容，也不自動淘汰舊項目。集合及其獨立且預設啟用的程式碼包含選項不會持久儲存，於專案關閉或外掛程式卸載時捨棄。收集不改變既有複製歷史、狀態列、評論計數或統計。作業系統及外部剪貼簿歷史遵循各自原則。

無需作用中編輯器即可從子選單或 Find Action 執行 **複製整個上下文集合**，沒有預設快捷鍵。目前格式、範本和空白修剪設定套用於已擷取的路徑與程式碼。內建格式為同一位置的快照加入固定擷取編號與 UTC 時間，自訂範本保留原有替換語意。任何項目輸出為空白都會阻止複製。輸出超過 256 KiB 時需確認，超過 4 MiB 時禁止複製；快照、參照與大小警告在同一對話框確認。複製後保留集合，不新增歷史或 gutter 標記，並遵循通知設定、選用統計與獨立評論條件。跨專案以最新外掛程式複製要求為準，包括歷史與狀態列重新複製；原生 Copy 及外部剪貼簿歷史不在此排序範圍內。請參閱 [#75](https://github.com/hon454/copy-selection-context/issues/75) 的[輸出契約](docs/development/context-collection-output-contract.md)與[範例選取內容](docs/samples/context-collection/README.md)。管理工具視窗與真實截圖將在 [#74](https://github.com/hon454/copy-selection-context/issues/74) 實作。

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
./gradlew allTests       # Run the complete test suite

# Windows
gradlew.bat buildPlugin
gradlew.bat runIde
gradlew.bat allTests
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
