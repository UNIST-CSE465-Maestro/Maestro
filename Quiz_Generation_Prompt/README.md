# QG Production Prompts — MCQ-only MVP

이 폴더는 Maestro QG 모듈의 MCQ-only MVP 운영용 프롬프트를 정의한다.

이번 버전에서는 SHORT 문제를 제외하고 MCQ만 생성한다.

지원 문제 유형:

```text
1. MCQ single_answer
2. MCQ multiple_select
```

---

## 1. 왜 MCQ-only로 변경했는가?

기존에는 MCQ와 SHORT를 모두 지원하는 구조였다.

기존 프롬프트 구조:

```text
server/prompts/qg/
├── mcq_system.txt
├── mcq_user_template.txt
├── short_system.txt
├── short_user_template.txt
└── README.md
```

하지만 MVP 단계에서는 SHORT 자동채점이 다음 문제를 만들 수 있다.

- 수식/기호 답변 처리 문제
- 단어/구/문장 구분 문제
- 한국어/영어 혼용 문제
- 대소문자, 복수형, 숫자 표현 normalization 문제
- semantic grading 또는 LLM judge 필요 가능성
- KT 이벤트로 변환할 때 응답 형식이 복잡해짐

따라서 MVP에서는 MCQ만 우선 적용한다.

MCQ는 자동채점이 단순하고, 응답 로그가 명확하며, KT 이벤트로 변환하기 쉽다.

---

## 2. 운영용 설계 방향

기존 프롬프트는 상세한 규칙과 few-shot 예시를 포함해 실험용으로는 좋았지만, 운영 환경에서 매번 넣기에는 토큰 사용량이 크다.

운영 환경에서는 매번 LLM API 호출 시 프롬프트와 retrieved_chunk가 함께 들어가야 하므로, 긴 프롬프트는 다음 문제가 있다.

- API 비용 증가
- 응답 지연 증가
- retrieved_chunk를 넣을 수 있는 공간 감소
- 규칙이 너무 많아져 모델이 일부 조건을 놓칠 가능성 증가

따라서 운영용에서는 하나의 긴 통합 프롬프트를 매번 넣지 않고, MCQ 유형과 Bloom Level에 맞게 필요한 조각만 조립한다.

---

## 3. 파일 구성

```text
qg_mcq_only_prod_prompts/
├── common_system_prod.txt
├── mcq_single_user_prod.txt
├── mcq_multiple_user_prod.txt
├── bloom_requirements.md
├── mcq_validation_and_retry.md
└── README.md
```

---

## 4. 프롬프트 조립 방식

### MCQ single_answer

사용 조건:

```text
question_type = MCQ
mcq_type = single_answer
권장 Bloom Level = 1~3
```

조립:

```text
common_system_prod.txt
+ mcq_single_user_prod.txt
+ requested bloom_requirement
+ retrieved_chunk
+ concept/domain metadata
```

---

### MCQ multiple_select

사용 조건:

```text
question_type = MCQ
mcq_type = multiple_select
권장 Bloom Level = 4~6
```

조립:

```text
common_system_prod.txt
+ mcq_multiple_user_prod.txt
+ requested bloom_requirement
+ retrieved_chunk
+ concept/domain metadata
```

---

## 5. 프롬프트에 삽입해야 하는 변수

각 user template에는 아래 변수가 들어간다.

```text
{retrieved_chunk}
{selection_mode}
{book_title}
{chapter_title}
{section_title}
{concept_name}
{bloom_level}
{bloom_verb}
{bloom_requirement}
```

---

## 6. Bloom Level 삽입 방식

전체 Bloom Level 설명을 매번 넣지 않는다.

서버 또는 앱은 `bloom_requirements.md`의 내용 중 요청된 Level에 해당하는 설명만 `{bloom_requirement}`에 삽입한다.

---

## 7. few-shot 사용 여부

현재 MCQ-only 운영용 기본안에서는 few-shot을 포함하지 않는다.

Claude 기반 1차 테스트 결과, few-shot 없이도 다음 항목이 정상 동작했다.

- MCQ single_answer 생성
- MCQ multiple_select 생성
- 근거 부족 시 insufficient_context 반환
- source_sentence 원문 유지

따라서 운영용 기본안은 few-shot 없이 사용한다.

단, 특정 문제 유형에서 반복 오류가 발생하면 해당 유형에만 짧은 few-shot 1개를 추가하는 것을 고려한다.

---

## 8. source_sentence 정책

모든 문제는 source_sentence 또는 source_sentences를 포함해야 한다.

source는 반드시 retrieved_chunk 안에 실제로 존재하는 연속된 원문 문자열이어야 한다.

금지:

- 원문 요약
- 원문 재작성
- 표현 변경
- 원문에 없는 문장 생성
- 여러 문장을 섞어서 만든 문장

---

## 9. 정답 및 해설 출력 정책

모든 문제는 앱에서 정답 표시와 해설 표시를 분리해서 사용할 수 있도록 아래 필드를 포함한다.

```text
answer
answer_text
explanation
final_explanation
```

역할:

- answer: 채점용 정답 값
- answer_text: 사용자에게 보여줄 정답 텍스트
- explanation: 정답/오답 판단 근거
- final_explanation: 학습자가 정답 확인 후 볼 최종 해설

---

## 10. 검증 로직

프롬프트만 믿고 결과를 바로 사용자에게 보여주면 안 된다.

서버 또는 앱은 LLM 응답을 받은 뒤 반드시 검증해야 한다.

검증 항목:

```text
1. JSON parsing 가능 여부
2. question_type == "MCQ"
3. mcq_type == "single_answer" 또는 "multiple_select"
4. grading_method 일치 여부
5. choices가 A/B/C/D 모두 있는지
6. single_answer answer가 A/B/C/D 중 하나인지
7. multiple_select answer가 배열인지
8. answer_text 형식이 answer와 맞는지
9. source_sentence가 retrieved_chunk 안에 실제 존재하는지
10. source_sentences의 각 문장이 retrieved_chunk 안에 실제 존재하는지
```

---

## 11. 오류 처리

검증 실패 시 흐름:

```text
1차 LLM 응답
→ 서버/앱 검증 실패
→ 동일 retrieved_chunk와 동일 설정으로 1회 재시도
→ 재검증
→ 다시 실패하면 insufficient_context 처리
```

재시도 후에도 실패하면 사용자에게 잘못된 문제를 보여주지 않는다.

---

## 12. API 기반과 앱 내부 기반

### 서버/API 기반

```text
사용자 입력
→ 서버가 prompt template 선택
→ 서버가 retrieved_chunk, bloom_requirement 등 삽입
→ LLM API 호출
→ 서버 검증
→ 통과 시 앱 표시
→ 실패 시 재시도 또는 insufficient_context
```

### 앱 내부 기반

```text
사용자 입력
→ 앱이 prompt template 선택
→ 앱이 retrieved_chunk, bloom_requirement 등 삽입
→ 앱에서 LLM API 직접 호출
→ 앱 내부 검증
→ 통과 시 문제 표시
→ 실패 시 재요청 또는 insufficient_context
```

서버가 없더라도 구조는 유지할 수 있다.
단, 서버가 하던 검증 로직을 앱 내부에서 수행해야 한다.
