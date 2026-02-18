# Copy Selection Context

[![Build](https://github.com/hon454/copy-selection-context/actions/workflows/build.yml/badge.svg)](https://github.com/hon454/copy-selection-context/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context.svg)](https://plugins.jetbrains.com/plugin/com.github.hon454.copy-selection-context)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.github.hon454.copy-selection-context.svg)](https://plugins.jetbrains.com/plugin/com.github.hon454.copy-selection-context)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context.svg)](LICENSE)

> Copy file path + line numbers + code to clipboard in one shortcut — formatted for AI assistants.

AI 코딩 어시스턴트(Claude, ChatGPT 등)에게 코드 컨텍스트를 전달할 때, 파일 경로와 라인 번호를 일일이 타이핑하고 계신가요? **Copy Selection Context**는 한 번의 단축키로 `@path#Lline` 형식의 컨텍스트를 클립보드에 복사합니다.

## Features

- **One-shortcut copy** — `Ctrl+Alt+C` 하나로 파일 경로 + 라인 번호 복사
- **Relative or absolute paths** — 프로젝트 상대 경로 / 절대 경로 선택
- **Code content included** — 마크다운 코드 블록으로 선택한 코드까지 포함 가능
- **Copy history** — `Ctrl+Alt+H`로 최근 복사 이력 조회
- **GitHub/GitLab permalink** — 선택한 라인의 Git 퍼머링크를 바로 복사
- **Smart line handling** — 선택 없이 커서만 있으면 현재 줄 번호를 복사
- **Context menu** — 에디터 우클릭 메뉴에서 모든 액션 접근
- **Cross-platform** — Windows, macOS, Linux 모두 지원

## Installation

### From JetBrains Marketplace

1. `File` → `Settings` → `Plugins`
2. **"Copy Selection Context"** 검색
3. `Install` 클릭

### From Disk

1. [Releases](https://github.com/hon454/copy-selection-context/releases) 페이지에서 최신 `.zip` 다운로드
2. `File` → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. 다운로드한 `.zip` 선택 → IDE 재시작

## Usage

### Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+C` | `Cmd+Alt+C` |
| Show Copy History | `Ctrl+Alt+H` | `Ctrl+Alt+H` |

> 단축키는 `Settings` → `Keymap`에서 변경할 수 있습니다.

### Context Menu

에디터에서 우클릭 → **Copy Selection Context** 서브메뉴에서 개별 액션 선택:

| Action | Description |
|--------|-------------|
| Copy Selection Context | 설정에 따라 경로 + 라인 복사 (메인 액션) |
| Copy Relative Path with Line Numbers | 프로젝트 상대 경로로 복사 |
| Copy Absolute Path with Line Numbers | 절대 경로로 복사 |
| Copy with Code Content | 경로 + 라인 + 코드 블록 복사 |
| Copy GitHub/GitLab Permalink | Git 원격 저장소 퍼머링크 복사 |
| Show Copy History | 최근 복사 이력 팝업 |

### Output Format

`@path#Lline` 형식으로 AI 어시스턴트에 바로 붙여넣기 가능합니다.

**경로만 (기본)**:
- 단일 라인: `@src/main/kotlin/App.kt#L42`
- 여러 라인: `@src/main/kotlin/App.kt#L250-253`

**코드 포함 (설정에서 활성화)**:
````
@src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

### Settings

`Settings` → `Tools` → `Copy Selection Context`에서 설정:

- **Path type** — Absolute (기본) 또는 Relative
- **Include code content** — 코드 블록 포함 여부

## Compatible IDEs

IntelliJ Platform 2024.3+ 기반 모든 IDE에서 동작합니다:

IntelliJ IDEA · Android Studio · PyCharm · WebStorm · PhpStorm · CLion · GoLand · Rider · RubyMine

## Development

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

./gradlew buildPlugin    # 플러그인 빌드
./gradlew runIde         # 개발용 IDE 실행
./gradlew test           # 테스트 실행
```

자세한 개발 및 배포 가이드는 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

## Support

이 플러그인이 유용하셨다면 커피 한 잔 사주세요!

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

## Author

Made by [@hon454](https://github.com/hon454)
