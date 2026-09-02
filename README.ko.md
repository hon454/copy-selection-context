# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**[English](README.md)** · **한국어** · **[简体中文](README.zh-CN.md)** · **[繁體中文](README.zh-TW.md)** · **[日本語](README.ja.md)**

> 파일 경로 + 라인 번호 + 코드를 한 번의 단축키로 클립보드에 복사 — AI 어시스턴트에 바로 붙여넣기 가능한 형식으로.

AI 코딩 어시스턴트(Claude, ChatGPT 등)에게 코드 컨텍스트를 전달할 때, 파일 경로와 라인 번호를 일일이 타이핑하고 계신가요? **Copy Selection Context**는 한 번의 단축키로 `@path#Lline` 형식의 컨텍스트를 클립보드에 복사합니다.

[![Get from JetBrains Marketplace](https://img.shields.io/badge/Get%20from-JetBrains%20Marketplace-000000?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)

## 기능

- **원클릭 복사** — `Ctrl+Alt+C` 하나로 파일 경로 + 라인 번호 복사
- **상대/절대 경로** — 프로젝트 상대 경로 또는 절대 경로 선택
- **유연한 출력 형식** — Claude Code 참조, Path:Line 출력, 사용자 정의 템플릿 지원
- **코드 내용 포함** — 마크다운 코드 블록으로 선택한 코드까지 포함 가능
- **복사 이력** — `Ctrl+Alt+H`로 최근 복사 이력 조회
- **GitHub/GitLab 퍼머링크** — 선택한 라인의 Git 퍼머링크를 바로 복사
- **복사 피드백** — 복사한 라인 표시, 선택적 알림, 상태 표시줄의 마지막 복사 내용 제공
- **배려 있는 리뷰 경로** — 충분히 사용한 뒤 버전당 한 번의 솔직한 리뷰 요청과 수동 Marketplace 링크 제공
- **다중 caret 컨텍스트** — 각 caret을 독립적으로 형식화하고 경로/코드 블록 사이를 빈 줄로 구분
- **정확한 선택 끝 처리** — IntelliJ의 `selectionEnd - 1`을 마지막 포함 offset으로 사용해 불필요한 다음 줄 제외
- **로컬라이즈된 IDE UI** — 액션, 설정, 알림, 이력, 상태, 형식 이름을 영어, 한국어, 일본어, 중국어 간체 및 번체로 제공
- **스마트 라인 처리** — 선택 없이 커서만 있으면 현재 줄 번호를 복사
- **컨텍스트 메뉴** — 에디터 우클릭 메뉴에서 모든 액션 접근
- **크로스 플랫폼** — Windows, macOS, Linux 모두 지원

## 설치

### JetBrains Marketplace에서 설치

1. `File` → `Settings` → `Plugins`
2. **"Copy Selection Context"** 검색
3. `Install` 클릭

### 파일에서 직접 설치

1. [Releases](https://github.com/hon454/copy-selection-context/releases) 페이지에서 최신 `.zip` 다운로드
2. `File` → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. 다운로드한 `.zip` 선택 → IDE 재시작

## 릴리스 다운로드 검증

`.github/workflows/release.yml`이 첨부한 플러그인 ZIP이 canonical 릴리스 산출물입니다. signing credential이 구성되면 서명을 검증한 `-signed.zip`이 canonical이며, 같은 파일이 변경 없이 JetBrains Marketplace에도 게시됩니다. signing credential이 없으면 workflow가 canonical ZIP을 unsigned로 명확히 표시하고 Marketplace 게시를 건너뜁니다. IntelliJ Platform Gradle Plugin은 빌드 JVM과 운영체제를 `META-INF/MANIFEST.MF`에 기록하므로, 로컬 빌드는 기능적으로 같더라도 환경이 다르면 바이트까지 같지 않을 수 있습니다. 따라서 모든 GitHub Release에는 정확히 게시된 ZIP에 대한 `SHA256SUMS`와 GitHub artifact attestation이 포함됩니다.

두 자산을 모두 다운로드한 뒤 Linux에서 checksum을 검증합니다:

```bash
gh release download v1.2.0 \
  --repo hon454/copy-selection-context \
  --pattern '*.zip' \
  --pattern SHA256SUMS
sha256sum --check SHA256SUMS
```

macOS에서는 표준 `shasum` 명령을 사용합니다:

```bash
shasum -a 256 --check SHA256SUMS
```

이어서 GitHub가 같은 ZIP을 이 저장소의 canonical 릴리스 워크플로와 태그에서 생성된 것으로 attest했는지 검증합니다. 다운로드한 정확한 파일명을 사용하세요. signed 릴리스는 아래처럼 `-signed.zip` suffix가 있고, 명시적으로 unsigned인 릴리스에는 없습니다(필요하면 버전을 바꾸세요):

```bash
gh attestation verify copy-selection-context-1.2.0-signed.zip \
  --repo hon454/copy-selection-context \
  --signer-workflow hon454/copy-selection-context/.github/workflows/release.yml \
  --source-ref refs/tags/v1.2.0
```

## 사용법

### 단축키

| 액션 | Windows/Linux | macOS |
|------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+C` | `Cmd+Alt+C` |
| 복사 이력 보기 | `Ctrl+Alt+H` | `Ctrl+Alt+H` |

> 단축키는 `Settings` → `Keymap`에서 변경할 수 있습니다.

### 컨텍스트 메뉴

에디터에서 우클릭 → **Copy Selection Context** 서브메뉴에서 개별 액션 선택:

| 액션 | 설명 |
|------|------|
| Copy Selection Context | 설정에 따라 경로 + 라인 복사 (메인 액션) |
| Copy Relative Path with Line Numbers | 프로젝트 상대 경로로 복사 |
| Copy Absolute Path with Line Numbers | 절대 경로로 복사 |
| Copy with Code Content | 경로 + 라인 + 코드 블록 복사 |
| Copy GitHub/GitLab Permalink | Git 원격 저장소 퍼머링크 복사 |
| Show Copy History | 최근 복사 이력 팝업 |

### 출력 형식

메인 액션과 개별 경로/코드 액션은 설정에서 선택한 출력 형식을 사용합니다. 경로 구분자는 슬래시(`/`)로 통일됩니다.

각 caret은 고유한 1부터 시작하는 포함 범위와 선택적 코드 블록을 만들며, 블록 사이는 빈 줄로 구분됩니다. IntelliJ 선택 끝은 exclusive이므로 `selectionEnd - 1`이 마지막 포함 라인을 결정하며, 다음 줄 시작에서 끝난 선택은 그 줄을 포함하지 않습니다.

**Claude Code (`claude`, 기본)**:
- 단일 라인: ` @src/main/kotlin/App.kt#L42 `
- 여러 라인: ` @src/main/kotlin/App.kt#L250-253 `

**Path:Line (`pathline`)**:
- 단일 라인: `src/main/kotlin/App.kt:42`
- 여러 라인: `src/main/kotlin/App.kt:250-253`

**사용자 정의 템플릿 (`template`)**:
- Path and Range, Claude Reference, With Code Block 프리셋으로 시작하거나 직접 템플릿 입력
- 일반 복사 액션이 채우는 변수: `{path}`, `{line}`, `{range}`, `{code}`, `{lang}`, `{filename}`
- 설정 화면에서 결과를 미리 보고 알 수 없는 변수를 확인

**코드 포함** (Claude Code와 Path:Line은 코드 블록을 추가하며, 사용자 정의 템플릿은 `{code}`로 배치):
````
 @src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

별도 **Copy GitHub/GitLab Permalink** 액션은 백그라운드 스레드에서 일반 저장소 또는 linked worktree 메타데이터를 읽고 각 caret의 커밋 고정 URL을 만듭니다. 가장 최근 요청만 클립보드를 갱신할 수 있습니다. 원격 주소나 커밋을 확인할 수 없으면 오류를 알리고 클립보드는 변경하지 않습니다.

### 이력, 알림 및 상태 표시

- 일반 경로/코드 복사 액션은 프로젝트별 이력 앞에 항목을 추가합니다. `Ctrl+Alt+H`를 누르면 저장된 항목을 볼 수 있고, 항목을 선택하면 전체 내용을 다시 복사합니다.
- 복사 알림은 기본으로 켜져 있으며 끌 수 있습니다. 일반 경로/코드 복사와 Git 퍼머링크 복사 후에 표시됩니다.
- 알림이 켜진 지원 UI 환경에서 실행한 일반 복사만 리뷰 카운터를 증가시키며, 알림 비활성·테스트·headless·미지원 환경에서는 카운터와 프롬프트 상태가 모두 변경되지 않습니다. 한 IDE 세션의 10번째 유효 복사에서 해당 플러그인 버전에 한 번만 비모달 솔직한 리뷰 요청을 표시할 수 있습니다. **Review on Marketplace**는 공식 [Marketplace 리뷰 페이지](https://plugins.jetbrains.com/plugin/30262-copy-selection-context/reviews)를 열고, **Later**는 현재 버전의 남은 요청을 건너뛰며, **Don't ask again**은 요청을 영구적으로 억제합니다. 알림을 끄면 설정의 수동 링크만 사용할 수 있습니다.
- 정확한 세션 횟수는 저장하지 않습니다. 로컬 비로밍 `copySelectionReview.xml`에는 `lastPromptedVersion`, 영구 억제 선택, Marketplace 페이지를 열었는지만 보관하며 복사 내용, 파일 데이터, 분석, 리뷰 결과, 텔레메트리 또는 자동 네트워크 요청은 전혀 관여하지 않습니다.
- 일반 복사는 활성 에디터의 거터 표시를 교체하고 상태 표시줄 위젯에 prefix를 포함해 최대 40자인 단일 라인, Unicode-safe, markup-escaped 미리보기를 표시합니다. 위젯을 클릭하면 마지막 전체 값을 다시 복사합니다.
- 선택적 로컬 사용 분석은 성공한 일반 복사 액션을 출력 형식과 감지된 파일 언어별로 집계합니다. 설정에서 모든 카운터의 불변 스냅샷을 확인하고 확인 절차 후 초기화할 수 있습니다. 기본으로 꺼져 있고 로컬 IDE 애플리케이션 설정에만 저장되며 절대 전송되지 않습니다.

### 설정

`Settings` → `Tools` → `Copy Selection Context`에서 설정:

- **Path type** — Absolute (기본) 또는 Relative
- **Output format** — Claude Code (기본), Path:Line 또는 Custom Template
- **Custom format template** — 프리셋을 선택하거나 접근성 라벨과 변수 검증이 있는 6행 다중 라인 편집기 및 포커스 가능한 6행 실시간 미리보기 사용
- **Include code content** — 선택한 코드 또는 선택이 없을 때 현재 라인을 포함 (기본: 끔)
- **Trim code whitespace** — 포함한 코드 앞뒤의 공백 제거 (기본: 끔)
- **Show copy notifications** — 지원하는 복사 액션 후 풍선 알림 표시 (기본: 켬)
- **Review on Marketplace** — 프롬프트를 강제로 표시하지 않고 공식 리뷰 페이지를 수동으로 열기
- **Copy history size** — 프로젝트별 항목 0~100개 보관 (기본: 10), `0`이면 이력을 비활성화하고 삭제
- **Local usage analytics** — 이 기기에만 저장되는 선택적 전체, 출력 형식 및 언어 카운터 확인·초기화 (기본: 끔, 절대 전송하지 않음)

복사 이력에는 복사한 코드가 포함될 수 있습니다. 이 데이터는 IDE의 로컬 비로밍 작업 공간에만 저장되며 공유 가능한 프로젝트 설정에는 기록되지 않습니다. 저장 항목은 UTF-8 콘텐츠 기준 각각 256 KiB, 프로젝트 전체 이력은 2 MiB로 제한됩니다. 256 KiB를 넘는 결과도 전체 내용은 정상적으로 복사되지만 이력에는 추가되지 않습니다. 설정한 항목 수 또는 전체 바이트 한도를 넘으면 가장 오래된 항목부터 즉시 제거됩니다. 이력 팝업 하단의 **모든 히스토리 삭제**로 전체 항목을 지울 수 있습니다. `copySelectionHistory.xml`에서 마이그레이션된 데이터를 포함한 기존 이력도 로드할 때 같은 한도로 정규화되며, 이후 IDE가 레거시 파일을 정리합니다.

#### 설정 화면

![Copy Selection Context 설정 화면](docs/images/settings-copy-selection-context.png)

하나의 화면에서 경로 타입, 출력과 템플릿, 코드 처리, 알림, 리뷰 접근, 이력, 로컬 분석을 함께 조정할 수 있습니다.

## 호환 IDE

IntelliJ Platform 2024.3+ 기반 모든 IDE에서 동작합니다:

IntelliJ IDEA · Android Studio · PyCharm · WebStorm · PhpStorm · CLion · GoLand · Rider · RubyMine

## 개발

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

# Unix / macOS
./gradlew buildPlugin    # 플러그인 ZIP 빌드
./gradlew runIde         # 플러그인이 설치된 개발용 IDE 실행
./gradlew allTests       # 전체 테스트 스위트 실행

# Windows
gradlew.bat buildPlugin
gradlew.bat runIde
gradlew.bat allTests
```

자세한 개발 및 배포 가이드는 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

테스트 모음에는 실제 IntelliJ 에디터/액션/클립보드와 비동기 퍼머링크 흐름을 검증하는 `CopySelectionActionFixtureTest`가 포함됩니다. CI는 아티팩트 게시 전에 전체 테스트, 프로젝트 및 구조 검사, `verifyPlugin` 호환성 검사, 플러그인 패키징, ZIP 검사와 진단 업로드를 실행합니다.

## 후원

이 플러그인이 유용하셨다면 커피 한 잔 사주세요!

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## 라이선스

Apache License 2.0 — 자세한 내용은 [LICENSE](LICENSE)를 참고하세요.

## 만든 사람

[@hon454](https://github.com/hon454)
