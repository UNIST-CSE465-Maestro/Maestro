# Bloom Level Requirements for MCQ-only QG

서버 또는 앱은 QG 프롬프트를 조립할 때 요청된 Bloom Level에 해당하는 요구 조건만 `{bloom_requirement}`에 삽입한다.

전체 Bloom Level 설명을 매번 넣지 않고, 필요한 Level의 설명만 넣어 토큰 사용량을 줄인다.

---

## Level 1 — Remember

정의, 용어, 명시된 사실을 기억하거나 확인하는 객관식 문제를 생성한다.

문제는 학습 자료에 직접 등장하는 정보를 기반으로 해야 한다.

권장 MCQ 유형: single_answer

---

## Level 2 — Understand

개념의 의미, 역할, 관계, 구성 요소를 이해해야 답할 수 있는 객관식 문제를 생성한다.

문제는 단순 암기보다 개념 이해를 요구해야 하지만, 정답은 반드시 학습 자료 안에서 확인 가능해야 한다.

권장 MCQ 유형: single_answer

---

## Level 3 — Apply

학습 자료의 규칙, 조건, 절차를 간단한 상황에 적용하는 객관식 문제를 생성한다.

원문을 그대로 묻는 recall 문제가 아니라, 자료의 원리를 간단한 상황에 적용해야 한다.

권장 MCQ 유형: single_answer

---

## Level 4 — Analyze

조건, 차이, 관계, 구조를 분석하는 객관식 문제를 생성한다.

여러 선택지를 비교하거나, 조건에 따라 참/거짓을 판단하게 해야 한다.

권장 MCQ 유형: multiple_select

---

## Level 5 — Evaluate

학습 자료에 제시된 기준, 원리, 조건을 바탕으로 판단하거나 평가하는 객관식 문제를 생성한다.

학습 자료에 판단 기준이 충분하지 않으면 insufficient_context를 반환해야 한다.

권장 MCQ 유형: multiple_select

---

## Level 6 — Create

학습 자료의 원리와 일치하는 절차, 예시, 구성, 설명을 선택하게 하는 객관식 문제를 생성한다.

완전한 자유 창작 문제가 아니라, 자료의 원리에 부합하는 산출물을 고르는 문제여야 한다.

학습 자료에 생성 판단 기준이 충분하지 않으면 insufficient_context를 반환해야 한다.

권장 MCQ 유형: multiple_select
