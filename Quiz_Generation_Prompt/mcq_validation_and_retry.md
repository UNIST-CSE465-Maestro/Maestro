# MCQ-only QG Validation and Retry Guide

이 문서는 MCQ-only QG를 서버 또는 앱 내부에서 실행할 때 검증해야 하는 항목과 오류 처리 방식을 정의한다.

현재 MVP에서는 SHORT를 제외하고 MCQ만 사용한다.

지원 문제 유형:
- MCQ single_answer
- MCQ multiple_select

---

## 1. 프롬프트 조립에 필요한 값

서버 또는 앱은 LLM 호출 전에 아래 값을 준비한다.

```json
{
  "retrieved_chunk": "선택된 단원/섹션의 paragraph 또는 retrieval 결과",
  "selection_mode": "domain-based 또는 concept-based",
  "book_title": "교재명",
  "chapter_title": "챕터명",
  "section_title": "섹션명",
  "concept_name": "대상 개념 또는 섹션 핵심 개념",
  "bloom_level": 4,
  "bloom_verb": "analyze",
  "bloom_requirement": "해당 Bloom Level 요구 조건",
  "question_type": "MCQ",
  "mcq_type": "multiple_select"
}
```

---

## 2. 템플릿 선택 로직

### MCQ single_answer

사용 조건:
- question_type = MCQ
- mcq_type = single_answer
- 권장 Bloom Level = 1~3

조립:
```text
common_system_prod.txt
+ mcq_single_user_prod.txt
+ requested bloom_requirement
+ retrieved_chunk
+ metadata
```

### MCQ multiple_select

사용 조건:
- question_type = MCQ
- mcq_type = multiple_select
- 권장 Bloom Level = 4~6

조립:
```text
common_system_prod.txt
+ mcq_multiple_user_prod.txt
+ requested bloom_requirement
+ retrieved_chunk
+ metadata
```

---

## 3. LLM 응답 검증 항목

LLM 응답은 사용자에게 바로 보여주지 않는다.
서버 또는 앱이 아래 항목을 검증한다.

### 공통 검증

- JSON parsing 가능 여부
- 최상위 응답이 JSON object인지
- `question_type`이 `"MCQ"`인지
- `insufficient_context`가 boolean인지
- `bloom_level`이 요청 값과 일치하는지
- `target_concept`가 요청 concept와 일치하는지 또는 충분히 관련 있는지

---

## 4. MCQ single_answer 검증

검증 항목:

```text
question_type == "MCQ"
mcq_type == "single_answer"
grading_method == "exact_match"
choices가 A, B, C, D 모두 포함
answer가 "A", "B", "C", "D" 중 하나
answer_text가 choices[answer]와 일치하거나 의미상 동일
source_sentence가 retrieved_chunk 안에 실제 존재
short_type 또는 acceptable_answers 같은 SHORT 필드가 없음
```

source 검증:

```text
source_sentence in retrieved_chunk
```

---

## 5. MCQ multiple_select 검증

검증 항목:

```text
question_type == "MCQ"
mcq_type == "multiple_select"
grading_method == "exact_set_match"
choices가 A, B, C, D 모두 포함
answer가 배열
answer 배열 원소가 A, B, C, D 중 하나
answer_text가 배열
source_sentences가 A, B, C, D 모두 포함
source_sentences 각 값이 retrieved_chunk 안에 실제 존재
short_type 또는 acceptable_answers 같은 SHORT 필드가 없음
```

source 검증:

```text
source_sentences["A"] in retrieved_chunk
source_sentences["B"] in retrieved_chunk
source_sentences["C"] in retrieved_chunk
source_sentences["D"] in retrieved_chunk
```

---

## 6. insufficient_context 처리

LLM이 `insufficient_context: true`를 반환한 경우, 정해진 형식이면 통과 처리할 수 있다.

---

## 7. 오류 처리 및 재시도

검증 실패 시 흐름:

```text
1차 LLM 응답
→ 서버/앱 검증 실패
→ 동일 retrieved_chunk와 동일 설정으로 1회 재시도
→ 재검증
→ 다시 실패하면 insufficient_context 처리
```

재시도 프롬프트에는 실패 이유를 간단히 포함한다.

예:

```text
이전 출력은 검증에 실패했습니다.
실패 이유: multiple_select의 answer가 배열이 아닙니다.
같은 학습 자료와 설정으로 다시 생성하세요.
answer는 반드시 ["A", "C"] 형태의 배열이어야 합니다.
JSON 객체 하나만 출력하세요.
```

---

## 8. MCQ-only MVP 장점

SHORT를 제외하고 MCQ만 사용할 경우 장점:

- 자동채점이 단순함
- KT 이벤트로 변환하기 쉬움
- answer 형식 검증이 쉬움
- 수식/기호/phrase 답변 문제를 피할 수 있음
- 앱 구현 부담 감소
- 사용자의 응답 로그가 명확함

MCQ-only MVP에서도 Bloom Level 1~6은 다음 방식으로 어느 정도 커버 가능하다.

```text
Level 1~3 → MCQ single_answer
Level 4~6 → MCQ multiple_select
```
