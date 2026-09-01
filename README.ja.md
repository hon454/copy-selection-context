# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**[English](README.md)** · **[한국어](README.ko.md)** · **[简体中文](README.zh-CN.md)** · **[繁體中文](README.zh-TW.md)** · **日本語**

> ファイルパス、行番号、コードを1つのショートカットでクリップボードにコピー。AIアシスタントへそのまま貼り付けられる形式です。

ClaudeやChatGPTなどのAIコーディングアシスタントにコードのコンテキストを共有するとき、ファイルパスや行番号を手入力していませんか？ **Copy Selection Context**なら、1つのショートカットで`@path#Lline`形式のコンテキストをクリップボードにコピーできます。

## 機能

- **ショートカットでコピー** — `Ctrl+Alt+C`でファイルパスと行番号をすぐにコピー
- **相対パスまたは絶対パス** — プロジェクト相対パスと絶対パスから選択可能
- **コード内容を含める** — 選択したコードをMarkdownコードブロックとして追加可能
- **コピー履歴** — `Ctrl+Alt+H`で最近のコピー履歴を表示
- **GitHub/GitLabパーマリンク** — 選択した行のGitパーマリンクをコピー
- **スマートな行番号処理** — テキストが選択されていない場合は現在の行番号をコピー
- **コンテキストメニュー** — エディターの右クリックメニューからすべてのアクションにアクセス
- **クロスプラットフォーム** — Windows、macOS、Linuxに対応

## インストール

### JetBrains Marketplaceから

1. `File` → `Settings` → `Plugins`
2. **「Copy Selection Context」**を検索
3. `Install`をクリック

### ディスクから

1. [Releases](https://github.com/hon454/copy-selection-context/releases)ページから最新の`.zip`をダウンロード
2. `File` → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. ダウンロードした`.zip`を選択 → IDEを再起動

## 使い方

### キーボードショートカット

| アクション | Windows/Linux | macOS |
|------------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+C` | `Cmd+Alt+C` |
| Show Copy History | `Ctrl+Alt+H` | `Ctrl+Alt+H` |

> ショートカットは`Settings` → `Keymap`で変更できます。

### コンテキストメニュー

エディター内で右クリック → **Copy Selection Context**サブメニュー：

| アクション | 説明 |
|------------|------|
| Copy Selection Context | 設定に基づいてパスと行番号をコピー（メインアクション） |
| Copy Relative Path with Line Numbers | プロジェクト相対パスでコピー |
| Copy Absolute Path with Line Numbers | 絶対パスでコピー |
| Copy with Code Content | パス、行番号、コードブロックをコピー |
| Copy GitHub/GitLab Permalink | Gitリモートリポジトリのパーマリンクをコピー |
| Show Copy History | 最近のコピー履歴ポップアップを表示 |

### 出力形式

AIアシスタントにそのまま貼り付けられる`@path#Lline`形式で出力します。

**パスのみ（デフォルト）**：
- 1行：`@src/main/kotlin/App.kt#L42`
- 複数行：`@src/main/kotlin/App.kt#L250-253`

**コード内容を含める（設定で有効化）**：
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

- **Path type** — Absolute（デフォルト）またはRelative
- **Include code content** — コードブロックを含めるかどうか
- **Copy history size** — 最大100件を保持するか、`0`に設定して履歴を無効化

コピー履歴にはコピーしたコードが含まれる場合があります。データはIDEのローカルな非ローミングワークスペースにのみ保存され、共有可能なプロジェクト設定には書き込まれません。履歴ポップアップ下部の **Clear all history** ですべての項目を削除できます。最大件数を減らすと古い項目はすぐに削除され、以前 `copySelectionHistory.xml` に保存された履歴はローカルワークスペースストレージへ移行された後、IDEによって旧ファイルが削除されます。

#### 設定画面

![Copy Selection Contextの設定画面](docs/images/settings-copy-selection-context.png)

パスの種類、出力形式、コードを含めるかどうか、通知の動作、履歴オプションを1つの画面で設定できます。

## 対応IDE

IntelliJ Platform 2024.3以降をベースとするすべてのIDEで動作します：

IntelliJ IDEA · Android Studio · PyCharm · WebStorm · PhpStorm · CLion · GoLand · Rider · RubyMine

## 開発

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

開発とリリースの詳しいガイドについては、[CONTRIBUTING.md](CONTRIBUTING.md)をご覧ください。

## サポート

このプラグインが役に立ったら、ぜひコーヒーをごちそうしてください！

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## ライセンス

Apache License 2.0 — 詳細は[LICENSE](LICENSE)をご覧ください。

## 作者

[@hon454](https://github.com/hon454)が開発
