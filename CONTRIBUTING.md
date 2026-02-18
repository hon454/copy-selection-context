# Contributing to Copy Selection Context

## Development Setup

### Prerequisites

- JDK 21+
- IntelliJ IDEA (Community or Ultimate)

### Build & Run

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

./gradlew buildPlugin    # 플러그인 ZIP 빌드 (build/distributions/)
./gradlew runIde         # 플러그인이 설치된 개발용 IDE 실행
./gradlew test           # 테스트 실행
./gradlew verifyPlugin   # 플러그인 구조 검증
```

## Project Structure

```
src/main/kotlin/com/github/hon454/copyselectioncontext/
├── CopySelectionContextAction.kt    # 메인 통합 액션 (Ctrl+Alt+C)
├── CopySelectionBaseAction.kt       # 추상 베이스 (클립보드 로직)
├── CopyRelativePathAction.kt        # 상대 경로 (컨텍스트 메뉴)
├── CopyAbsolutePathAction.kt        # 절대 경로 (컨텍스트 메뉴)
├── CopyWithCodeContentAction.kt     # 경로 + 코드 블록 (컨텍스트 메뉴)
├── CopyGitPermalinkAction.kt        # GitHub/GitLab 퍼머링크
├── ShowCopyHistoryAction.kt         # 복사 이력 팝업
├── CopySelectionNotifier.kt         # 토스트 알림
├── CopySelectionStatusBarWidget.kt  # 상태바 위젯
├── CopySelectionSettings.kt         # 설정 영속화 (@Service + @State)
└── CopySelectionConfigurable.kt     # 설정 UI (Tools 메뉴)
```

## Plugin Signing (for Marketplace Publishing)

Marketplace에 배포하려면 플러그인 서명이 필요합니다.

### 인증서 생성

```bash
# 암호화된 개인 키 생성
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096

# 비암호화 개인 키 추출
openssl rsa -in private_encrypted.pem -out private.pem

# 인증서 체인 생성
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

### 환경 변수 설정

```bash
export CERTIFICATE_CHAIN=$(cat chain.crt)
export PRIVATE_KEY=$(cat private.pem)
export PRIVATE_KEY_PASSWORD="your-password"
export PUBLISH_TOKEN="your-jetbrains-token"
```

### 배포

```bash
./gradlew publishPlugin
```

## Commit Convention

[Conventional Commits](https://www.conventionalcommits.org/) 형식을 따릅니다.

```
type[(scope)]: concise subject

Body explaining WHY this change was made.
```

**Allowed types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `perf`, `style`, `build`, `asset`

## Pull Requests

- 하나의 PR에 하나의 기능/수정
- 수동 테스트 절차 포함
- 아키텍처 변경 시 AGENTS.md 업데이트
