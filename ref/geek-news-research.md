# GeekNews 리서치 정리

Maestro 앱 재구조화를 위한 GeekNews 관련 기사 리서치. 2026-04-13 기준.

---

## 1. Android 개발 환경 & 아키텍처

### [2025년에 안드로이드 앱을 만들기](https://news.hada.io/topic?id=19694)

**빌드 시스템:**
- Gradle + Convention Plugin으로 빌드 설정 관리
- Version Catalog로 의존성 버전 중앙 관리
- Build Cache + Build-scan으로 빌드 성능 분석/최적화
- Configuration Cache 및 Isolated Projects 마이그레이션 준비 권장

**모듈 구조:**
- `feature-api` / `feature-impl` 분리 권장
- feature-api: 데이터 모델 + 인터페이스
- feature-impl: 실제 구현
- 이점: 의존성 격리 → incremental build 및 build cache hit rate 상승

**라이브러리 스택:**
| 영역 | 추천 |
|------|------|
| HTTP | Retrofit |
| JSON | Kotlinx Serialization |
| 저장소 | Jetpack DataStore, Room |
| DI | Koin |
| UI | Jetpack Compose |
| 통신 | Flow (View ↔ ViewModel) |
| 이미지 | Coil |
| 코드스타일 | Ktlint |
| 아키텍처 검증 | Konsist |
| 테스트 | JUnit 4 (Google 공식 입장: JUnit 5 전환 계획 없음) |

**트렌드:** 스타트업은 Flutter 채택 경향, 대기업(Meta, OpenAI)은 네이티브 복귀.

### [Android 앱 개발 환경 토론](https://news.hada.io/topic?id=17250)

- Android Studio가 무겁지만 대안 부재, 네이티브 개발 시 필수
- heap size 조정으로 성능 개선 가능
- VSCode + Flutter 조합이 경량 대안이나, 네이티브에는 부적합
- 최신 에뮬레이터는 CPU/GPU 가속으로 실기기보다 효율적일 수 있음

### [Meta의 Java→Kotlin 대규모 전환](https://news.hada.io/topic?id=18401)

- 천만 라인 규모, 현재 절반 이상 변환 완료
- 전환 이유: 생산성, Null 안전성, 혼합 코드베이스의 빌드 속도 저하
- **Kotlinator** 자동화 도구 개발 (6단계: Deep Build → Preprocessing 50단계 → Headless J2K → Postprocessing 150단계 → Linters → Build Error Fix)
- IntelliJ J2K를 headless로 실행 가능하게 수정 (ApplicationStarter 확장)
- 활성 개발 파일 우선 변환 전략
- 도구: Editus(전처리), JetBrains PSI(변환), Nullsafe/NullAway(검증)

### [넷플릭스 Kotlin Multiplatform 도입](https://news.hada.io/topic?id=3132)

- Kotlin으로 비즈니스 로직 작성 → Kotlin/Native로 컴파일 → Android/iOS 공유
- 약 50% 코드가 플랫폼 독립적
- Android JetPack Compose, SwiftUI 호환
- Kotlin/JS로 웹 확장 검토 중
- 참고: Dropbox의 C++ 기반 코드 공유는 실패 → Swift/Kotlin으로 전환

---

## 2. LLM 앱 통합 & UI/UX

### [채팅 이후의 UI: LLM 시대의 UX 전환](https://news.hada.io/topic?id=20884)

원문: Allen Pike, ["Post-Chat UI"](https://allenpike.com/2025/post-chat-llm-ui)

**핵심 논점:** 채팅은 터미널로의 회귀이며, 기본 UX가 되어선 안 됨.

**7가지 진화 패턴:**
1. **문서 중심 UI** — 콘텐츠가 주, 채팅이 보조 (ChatGPT Canvas 방식)
2. **생성형 컨텍스트 메뉴** — 커서 위치에 따른 AI 명령 제안
3. **자연어 검색** — "출장 항공편 언제야?" 같은 직관적 쿼리
4. **선택→입력 전환** — 드롭다운 대신 자연어 입력
5. **인라인 피드백** — 스타일, 주장, 출처 실시간 제안
6. **자동 정리** — 산만한 파일 자동 구조화 (Figma AI)
7. **다음 행동 예측** — 패턴 기반 자동완성 (Cursor 탭)

**최종 단계:** LLM이 사용자 목적에 맞춰 UI 자체를 실시간 생성.

**Maestro 적용 포인트:**
- 올가미→LLM 호출 = "생성형 컨텍스트 메뉴" 패턴
- Claude 사이드바 = "문서 중심 + 보조 채팅" 패턴
- 향후: 선택 영역 기반 인라인 AI 피드백

### [LLM 애플리케이션을 위한 새로운 아키텍처](https://news.hada.io/topic?id=9596)

a16z 제시 LLM 앱 스택 (4계층):

1. **컨텍스트 데이터** — 임베딩 모델(OpenAI ada-002 등) + 벡터DB(Pinecone, Chroma, pgvector)
2. **프롬프트 생성/검색** — 오케스트레이션(LangChain, LlamaIndex), 외부 API 연동
3. **추론/실행** — GPT-4 주도, Claude(100k 컨텍스트), 오픈소스(LLaMA), 캐싱(Redis)
4. **운영** — 로깅(W&B), 검증(Guardrails)

**핵심 패턴: In-Context Learning** — 파인튜닝 없이, 관련 문서만 검색하여 프롬프트에 포함.

**Maestro 적용 포인트:**
- PDF 내용을 컨텍스트로 Claude에 전달하는 구조
- 올가미 선택 영역 → 이미지/텍스트 추출 → Claude API 호출

### [오프라인 AI 워크스페이스 구축기](https://news.hada.io/topic?id=22420)

- Ollama(로컬 LLM) + MCP(Model Context Protocol) 기반 3계층 구조
- Apple Container VM에서 격리 실행, Playwright로 브라우저 자동화
- 현실적 한계: 로컬 LLM 성능 격차 심각, 하드웨어 비용 높음
- **MCP 기반 도구 통합 설계**가 실용적이라는 평가

### [Ollama 기반 LLM 모바일 클라이언트 (MyOllama)](https://news.hada.io/topic?id=17910)

- Flutter 기반, `flutter_chat_ui` 라이브러리 활용
- IP 주소로 Ollama 호스트 연결, 모델 선택
- 커스텀 프롬프트(Instruction), 이미지 인식, 세션 관리
- 다국어 지원 (한국어/영어/일본어)

---

## 3. Claude API & Anthropic 생태계

### [Claude API CORS 지원 — 브라우저 직접 호출 가능](https://news.hada.io/topic?id=16433)

- HTTP 헤더 `"anthropic-dangerous-direct-browser-access": "true"` 추가로 활성화
- **BYOK(Bring Your Own Key) 패턴**: 사용자가 자신의 API 키 제공
- 보안 주의: 클라이언트 코드에 키 하드코딩 금지
- 내부 도구나 BYOK 앱에만 권장, 공개 앱에는 부적합

**Maestro 적용 포인트:**
- 현재 앱의 API 키 기반 네이티브 채팅 구현과 직결
- 사용자가 직접 API 키를 입력하는 BYOK 방식이 적합

### [Model Context Protocol (MCP) 오픈소스 공개](https://news.hada.io/topic?id=17951)

- AI 어시스턴트를 데이터 소스/도구와 연결하는 표준 프로토콜
- 개별 커넥터 대신 단일 프로토콜로 통합
- Google Drive, Slack, GitHub, Postgres 등 이미 지원
- Block, Apollo, Zed, Replit 등 도입 중

### [Agent Skills — 워크플로우 맞춤 AI](https://news.hada.io/topic?id=23713)

- 마크다운 기반, 폴더 단위 구성
- 필요 시점에만 로드 (효율적)
- 실행 가능한 코드(Python, Shell 등) 포함 가능
- Claude 앱/Code/API 전반에서 사용 가능
- 실제 사례: Box(문서 변환 자동화), Rakuten(보고서 생성 1일→1시간)

### [Anthropic Courses — 무료 온라인 강의](https://news.hada.io/topic?id=27118)

개발자 대상 핵심 강의:
1. **Claude Code in Action** — 개발 워크플로 통합 실습
2. **Building with the Claude API** — API 활용 전 과정
3. **Introduction to MCP** — Python으로 MCP 서버/클라이언트 구축
4. **MCP Advanced Topics** — 프로덕션 MCP 서버 고급 패턴
5. **Introduction to Agent Skills** — 스킬 생성/구성/공유

---

## 4. 소프트웨어 설계 일반

### [소프트웨어 설계를 위한 추상적, 구조적 사고](https://news.hada.io/topic?id=10378)

**설계 3단계:** 도메인 모델링 → 아키텍처 → 코드 작성

**추상적 사고:**
- 요소에서 공통점/관심사 추출 → 단순화 → 재해석
- 요소뿐 아니라 행동도 추상화 가능
- 과도한 추상화는 실체를 불명확하게 함

**구조적 사고:**
- MECE (겹치지 않게, 빈틈없이) 프레임워크
- 탑다운/바텀업 모델, 분류, 일반화

**코드 설계:**
- 로직의 3가지 측면: Function, Usecase, Aspect
- 리팩토링 6가지 관점: 패러다임, 코드 크기, 소유권, 중복, 수정 가능성, 의존성

---

## 5. PDF 도구 생태계

### [EmbedPDF — 오픈소스 JS PDF 뷰어](https://news.hada.io/topic?id=22541)

- PDFium(Google C++ 엔진) 기반, WebAssembly 컴파일
- 주석: 하이라이트, 스티키 노트, 자유 텍스트, **잉크(필기)**
- 플러그인 아키텍처, tree-shakeable
- React/Vue/Svelte/Preact/Vanilla JS 지원
- MIT 라이선스 (PDFium은 Apache 2.0)
- PDF.js 대비 커스터마이징 용이

---

## 핵심 시사점 (Maestro 재구조화 적용)

### 빌드 & 프로젝트 구조
- Version Catalog + Convention Plugin 도입
- feature-api/feature-impl 모듈 분리 고려 (빌드 캐시 최적화)
- Room 도입 검토 (현재 JSON 기반 → 구조화된 DB)

### 아키텍처
- MVVM + Clean Architecture (domain/data/presentation 분리)
- Flow 기반 View-ViewModel 통신
- Koin DI 도입

### LLM 통합
- BYOK 패턴으로 API 키 기반 네이티브 채팅 우선 구현
- "문서 중심 + 보조 채팅" UI 패턴 유지
- 올가미→LLM 호출 = "생성형 컨텍스트 메뉴" 패턴으로 설계
- In-Context Learning: PDF 내용/선택 영역을 컨텍스트로 활용

### 테스트
- JUnit 4 기반 단위 테스트 도입
- Konsist로 아키텍처 규칙 검증

### 향후
- MCP 연동 가능성 (도구 확장)
- Kotlin Multiplatform은 현 단계에서는 과도 (단일 플랫폼 집중)
