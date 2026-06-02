# MinerU vs ML Kit 로컬 PDF 파싱 테스트 비교

## 목적

Maestro는 기존에 백엔드에서 MinerU를 사용해 PDF를 파싱하고, 앱은 `content.md`와 `content.json`을 받아 저장하는 구조를 사용했다. 이번 테스트에서는 이 구조를 유지하면서, Android 태블릿 로컬에서 Google ML Kit Text Recognition으로 PDF 페이지를 OCR 처리해 `local_mlkit_content.md`와 `local_mlkit_content.json`을 별도로 생성하는 실험 버전을 추가했다.

핵심 질문은 다음과 같다.

- ML Kit만으로 MinerU의 `content.md` / `content.json`을 어느 정도 대체할 수 있는가?
- 생성된 파일 구조는 어떤 차이가 있는가?
- 검색, 선택 영역 퀴즈, LLM 질의, 추후 KT 데이터 수집에 어떤 방식이 더 적합한가?

## 테스트 파일 위치

앱 내부 저장소 기준으로 확인한 파일은 다음과 같다.

### MinerU 결과 예시

```text
files/documents/fbe28f93-d86b-4f12-bcb0-6ff42b77f600/content.md
files/documents/fbe28f93-d86b-4f12-bcb0-6ff42b77f600/content.json
```

파일 크기:

```text
content.md    32,820 bytes
content.json 182,964 bytes
```

### ML Kit 결과 예시

```text
files/documents/14c2e01a-8104-4dee-99f2-3956ca0d0040/local_mlkit_content.md
files/documents/14c2e01a-8104-4dee-99f2-3956ca0d0040/local_mlkit_content.json
```

파일 크기:

```text
local_mlkit_content.md     18,499 bytes
local_mlkit_content.json 1,915,228 bytes
```

주의: 위 두 결과는 초기 테스트 당시 태블릿에 남아 있던 실제 생성 파일 기준이며, 서로 같은 PDF를 직접 비교한 것은 아니다. 따라서 페이지 수와 총 텍스트량은 직접 성능 비교 수치가 아니라, 생성물의 구조와 특성을 이해하기 위한 샘플이다.

이 문제를 해결하기 위해 앱에 `MinerU + ML Kit 동시 추출` 모드를 추가했다. 이 모드를 선택하면 같은 PDF, 같은 documentId 아래에 다음 파일이 동시에 생성된다.

```text
content.md
content.json
local_mlkit_content.md
local_mlkit_content.json
mineru_mlkit_comparison.md
```

`mineru_mlkit_comparison.md`는 같은 PDF에서 생성된 MinerU 결과와 ML Kit 결과를 정량 비교하기 위한 자동 생성 리포트다.

## 생성 파일 이름과 저장 정책

기존 MinerU 결과는 그대로 유지한다.

```text
documents/{documentId}/content.md
documents/{documentId}/content.json
```

ML Kit 로컬 OCR 결과는 별도 파일로 저장한다.

```text
documents/{documentId}/local_mlkit_content.md
documents/{documentId}/local_mlkit_content.json
```

이렇게 분리한 이유는 다음과 같다.

- MinerU 결과를 덮어쓰지 않는다.
- 같은 PDF에 대해 서버 파싱 결과와 로컬 OCR 결과를 나란히 비교할 수 있다.
- MinerU가 실패했거나 아직 완료되지 않은 경우에도 ML Kit 결과를 fallback으로 사용할 수 있다.
- 향후 `content_source = mineru | mlkit | pdf_text_layer` 같은 비교 실험을 하기 쉽다.

## MinerU `content.md` 특징

MinerU가 생성한 Markdown은 문서 구조를 어느 정도 보존한다.

예시:

```md
# CSE 321 Database Systems

NULL, Join, Insert/Delete/Update

Junghoon Kim

Junghoon.kim@unist.ac.kr

# Aggregation

# Cars

<table><tr><td>Model</td><td>Maker</td><td>Price</td></tr><tr><td>Model Y</td><td>Tesla</td><td>40</td></tr><tr><td>iX</td><td>BMW</td><td>145</td></tr><tr><td>5 Series</td><td>BMW</td><td>90</td></tr></table>

What is the average price of the models from BMW?
How many models are there from BMW?
```

장점:

- 제목을 `#` heading으로 변환한다.
- 표를 HTML table 형태로 보존한다.
- 문서 전체를 LLM prompt에 넣기 좋은 형태로 정리한다.
- 일반 OCR보다 Markdown 문서로 읽기 쉽다.

한계:

- 그래프나 복잡한 이미지 안의 숫자 데이터는 정밀하게 복원하지 못할 수 있다.
- VLM/OCR이 잘못 인식한 표, 수식, 화살표, 기호는 그대로 들어간다.
- 서버 작업이 필요하므로 업로드, 대기, 네트워크 실패, 백그라운드 상태 관리 문제가 생긴다.

## MinerU `content.json` 특징

MinerU JSON은 문서 파싱 결과를 page/block/line/span 구조로 저장한다.

예시:

```json
{
  "pdf_info": [
    {
      "para_blocks": [
        {
          "bbox": [76, 130, 555, 173],
          "type": "title",
          "angle": 0,
          "lines": [
            {
              "bbox": [76, 130, 555, 173],
              "spans": [
                {
                  "bbox": [76, 130, 555, 173],
                  "type": "text",
                  "content": "CSE 321 Database Systems"
                }
              ]
            }
          ],
          "index": 1
        }
      ],
      "discarded_blocks": [],
      "page_size": [1920, 1080],
      "page_idx": 0
    }
  ],
  "_backend": "...",
  "_version_name": "..."
}
```

확인된 구조:

```text
pdf_info[]
  page_idx
  page_size
  para_blocks[]
    bbox
    type: title | text | table | image 등
    angle
    lines[]
      bbox
      spans[]
        bbox
        type
        content
  discarded_blocks[]
_backend
_version_name
```

장점:

- block type이 있다.
- title/text/table/image 등 semantic layout 정보가 들어갈 수 있다.
- crop 선택 영역에서 어떤 block이 겹치는지 찾기 좋다.
- LLM quiz 생성 시 source block과 page를 연결하기 좋다.

한계:

- 서버 모델 품질에 의존한다.
- PDF마다 block type 품질 차이가 크다.
- 그래프나 이미지 표는 단순 image block으로 남거나, 텍스트/숫자가 충분히 구조화되지 않을 수 있다.

## ML Kit `local_mlkit_content.md` 특징

ML Kit Markdown은 OCR 텍스트를 페이지별로 단순 나열한 형태다.

예시:

```md
# Page 1

Chapter 14-2

CSE 321 Database Systems

Functional Dependency & Normalization

Junghoon Kim

Junghoon.kim@unist.ac.kr

1

# Page 2

Recap

We have talked about a lot of abstract stuff

O Functional Dependencies

Armstrong's Axioms

Closures
```

장점:

- 서버 없이 바로 생성된다.
- 네트워크가 없어도 동작한다.
- PDF 이미지/스캔 문서에서도 텍스트를 어느 정도 뽑을 수 있다.
- 검색, 선택 영역 퀴즈, 빠른 LLM 질의 fallback으로 쓸 수 있다.

한계:

- `# Page N` 외의 heading 의미를 알지 못한다.
- 제목, 본문, 캡션, 표, 수식 같은 semantic 구분이 없다.
- 표는 row/column 구조가 아니라 OCR block 순서대로 흩어진다.
- 수식, 화살표, 특수 기호 인식이 약하다.

확인된 문제 예시:

```text
A → B
```

같은 functional dependency 화살표가 다음처럼 깨질 수 있다.

```text
A Ąć B
A > B
```

## ML Kit `local_mlkit_content.json` 특징

ML Kit JSON은 OCR 결과를 page/block/line/span 단위로 저장한다.

확인된 통계:

```text
source: mlkit_text_recognition_v2_latin
pages: 51
blocks: 701
lines: 835
spans/words: 3366
```

예시:

```json
{
  "source": "mlkit_text_recognition_v2_latin",
  "documentId": "14c2e01a-8104-4dee-99f2-3956ca0d0040",
  "generatedAt": 1779340000000,
  "pdf_info": [
    {
      "page_idx": 0,
      "page_size": [1800, 1350],
      "para_blocks": [
        {
          "id": "b0",
          "type": "text",
          "bbox": [1234, 154, 1648, 229],
          "text": "Chapter 14-2",
          "lines": [
            {
              "bbox": [1234, 154, 1648, 229],
              "text": "Chapter 14-2",
              "spans": [
                {
                  "bbox": [1234, 155, 1488, 229],
                  "content": "Chapter"
                },
                {
                  "bbox": [1515, 153, 1648, 226],
                  "content": "14-2"
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

구조:

```text
pdf_info[]
  page_idx
  page_size
  para_blocks[]
    id
    type: text
    bbox
    text
    lines[]
      bbox
      text
      spans[]
        bbox
        content
```

MinerU JSON과 비슷한 `pdf_info` / `para_blocks` 형태로 맞춰 두었기 때문에, 기존 crop extractor와 viewer fallback 로직에서 비교적 쉽게 사용할 수 있다.

하지만 `type`은 현재 항상 `text`에 가깝다. 즉 ML Kit JSON의 block은 semantic block이 아니라 OCR text block이다.

## 구조 비교

| 항목 | MinerU | ML Kit 로컬 |
|---|---|---|
| 실행 위치 | 백엔드 서버 | Android 태블릿 로컬 |
| 네트워크 필요 | 필요 | 불필요 |
| 결과 파일 | `content.md`, `content.json` | `local_mlkit_content.md`, `local_mlkit_content.json` |
| Markdown 품질 | 문서 구조 반영 | 페이지별 OCR 텍스트 나열 |
| JSON block type | title/text/table/image 등 가능 | 대부분 text |
| 표 처리 | HTML table 가능 | 단어/블록으로 분해됨 |
| 수식 처리 | 일부 LaTeX/텍스트화 가능 | 일반 OCR 수준 |
| 그래프/이미지 이해 | 제한적 VLM/layout 기반 | 거의 불가 |
| 검색 활용 | 좋음 | 좋음 |
| 선택 영역 텍스트 추출 | 좋음 | 가능 |
| LLM 퀴즈 source | 구조적 source 가능 | 텍스트 source 가능 |
| 실패 원인 | 서버, 네트워크, job 상태 | OCR 품질, 기기 성능 |
| 속도 | 서버 상태에 의존 | PDF 페이지 수와 기기 성능에 의존 |

## 실제 차이: 표 처리

MinerU Markdown에서는 표가 HTML table로 보존될 수 있다.

```html
<table>
  <tr><td>Model</td><td>Maker</td><td>Price</td></tr>
  <tr><td>Model Y</td><td>Tesla</td><td>40</td></tr>
  <tr><td>iX</td><td>BMW</td><td>145</td></tr>
</table>
```

ML Kit에서는 같은 종류의 표가 다음처럼 흩어질 가능성이 높다.

```text
Model
Maker
Price
Model Y
Tesla
40
iX
BMW
145
```

또는 JSON에서는 각 단어가 위치 bbox를 가진 text block/span으로 저장된다. 따라서 ML Kit 결과로 표를 복원하려면 추가 후처리가 필요하다.

필요한 후처리:

```text
1. 같은 y 범위에 있는 text block을 row로 묶기
2. x 좌표 기준으로 column cluster 만들기
3. 빈 cell과 merged cell 추정
4. 숫자/단위 column validation
5. table confidence 계산
```

## 실제 차이: 수식과 특수 기호

Database 강의 자료에서 functional dependency는 `A → B` 같은 형태를 자주 쓴다.

MinerU도 완벽하지 않지만, 문서 파싱 모델 또는 OCR 설정에 따라 수식/기호를 더 구조적으로 처리할 가능성이 있다.

ML Kit Latin OCR은 일반 텍스트 인식에 가깝기 때문에 다음 문제가 확인됐다.

```text
A → B  →  A Ąć B
A → C  →  A > C
```

따라서 ML Kit 결과를 그대로 퀴즈 생성에 쓰면 functional dependency, 수식, 기호 기반 문제에서 잘못된 문제가 생성될 수 있다.

보정 후보:

```text
Ąć, Ą, ć, > 패턴을 주변 문맥에서 → 로 정규화
FD 문맥에서 "A > B"를 "A → B" 후보로 변환
정규화 전/후 텍스트를 모두 저장
```

## 실제 차이: 파일 크기

이번 샘플에서 ML Kit JSON은 MinerU JSON보다 훨씬 컸다.

```text
MinerU content.json              182 KB
ML Kit local_mlkit_content.json 1.9 MB
```

이유:

- ML Kit JSON은 word/span bbox를 매우 많이 저장한다.
- 각 OCR word가 개별 bbox를 가진다.
- semantic block으로 압축되지 않고 OCR 원시 결과에 가깝다.

장점:

- 단어 단위 검색/하이라이트/선택 영역 매칭에 유리하다.

단점:

- 저장 공간이 커진다.
- 로딩/파싱 비용이 증가한다.
- LLM prompt에 바로 넣기에는 너무 세밀하다.

## Maestro 기능별 적합도

### 검색

ML Kit은 충분히 유용하다.

검색에 필요한 것은 다음이다.

```text
text
page_idx
bbox
```

ML Kit은 이 정보를 잘 제공한다. 특히 word/span bbox가 있어 하이라이트 위치 계산에 유리하다.

### 선택 영역 퀴즈

ML Kit은 fallback으로 유용하다.

선택 crop bbox와 OCR block bbox를 겹침 계산하면 해당 영역의 텍스트를 뽑을 수 있다.

다만 다음 경우는 약하다.

```text
표 일부 선택
수식 선택
그래프 선택
이미지 안 숫자 선택
다단 layout 선택
```

### 전체 문서 기반 퀴즈

MinerU가 더 적합하다.

이유:

- Markdown heading이 더 자연스럽다.
- 표가 HTML로 들어갈 수 있다.
- LLM이 읽기 좋은 문서 구조에 가깝다.

ML Kit은 전체 문서 퀴즈보다는 “선택 영역 텍스트 기반 퀴즈”에 더 적합하다.

### KT / concept profiling

MinerU가 더 좋다.

concept profiler는 문서 구조, heading, caption, table/figure context가 중요하다. ML Kit은 OCR 텍스트만 제공하므로 concept 추론이 불안정할 수 있다.

하지만 ML Kit은 다음 용도로 쓸 수 있다.

```text
MinerU 실패 시 임시 concept 후보 추출
사용자가 선택한 영역의 concept 추정
검색 기반 evidence 추출
```

## 장단점 요약

### MinerU 장점

- 문서 구조를 더 잘 보존한다.
- Markdown이 LLM prompt에 적합하다.
- 표를 HTML table로 보존할 수 있다.
- block type이 있어 layout 기반 기능에 유리하다.
- concept profiling, 전체 문서 퀴즈, retrieval에 더 적합하다.

### MinerU 단점

- 서버 필요.
- 네트워크 실패 가능.
- 추출 job 대기/재시도/백그라운드 관리 필요.
- 그래프/이미지 표의 정밀 숫자 복원은 여전히 부족하다.
- 서버 모델 버전과 설정에 따라 결과 품질이 흔들릴 수 있다.

### ML Kit 장점

- 완전 로컬 실행.
- 네트워크 없이 사용 가능.
- 빠른 fallback 가능.
- OCR word/span bbox가 자세하다.
- 검색/하이라이트/선택 영역 텍스트 추출에 좋다.
- 서버 추출 실패 상태에서도 최소한의 텍스트 기반 기능을 제공할 수 있다.

### ML Kit 단점

- 문서 semantic 구조를 모른다.
- heading/table/formula/image block 구분이 약하다.
- 표 row/column 복원 불가.
- 그래프 데이터 추출 불가.
- 수식/특수 기호 인식이 약하다.
- JSON 파일이 커질 수 있다.

## 현재 앱에서 권장 사용 방식

MinerU를 기본 고품질 파서로 유지한다.

```text
기본:
MinerU content.md/json
```

ML Kit은 로컬 fallback과 실험용 OCR 레이어로 사용한다.

```text
fallback:
local_mlkit_content.md/json
```

추천 우선순위:

```text
1. PDF text layer index
2. MinerU content.md/json
3. ML Kit local content
4. 선택 crop image 기반 VLM/서버 fallback
```

기능별 추천:

```text
검색:
PDF text layer 또는 ML Kit bbox 우선

전체 문서 퀴즈:
MinerU content.md 우선

선택 영역 퀴즈:
MinerU content.json 우선, 없으면 ML Kit local JSON

표/그래프 숫자 문제:
MinerU/ML Kit 모두 단독 사용 금지
별도 table extractor/chart digitizer 필요
```

## 다음 개선 제안

### 1. ML Kit 후처리 추가

```text
row grouping
column clustering
bullet/list normalization
special symbol normalization
page number/header/footer 제거
```

### 2. ML Kit 결과 압축

현재 JSON은 word/span bbox까지 모두 저장하므로 크다. 실험 후 필요 없는 세부 정보를 줄일 수 있다.

후보:

```text
full JSON: local_mlkit_content.json
compact JSON: local_mlkit_content_compact.json
```

compact 구조:

```json
{
  "page_idx": 0,
  "blocks": [
    {
      "text": "...",
      "bbox": [0, 0, 100, 100]
    }
  ]
}
```

### 3. MinerU와 ML Kit 병합

둘 중 하나만 쓰지 말고, 서로 보완하게 만들 수 있다.

```text
MinerU block structure
+ ML Kit word bbox
= 구조적 문서 + 정밀 검색/선택 좌표
```

예:

```text
MinerU title/text/table block을 기준으로 semantic layout 유지
각 block 내부 단어 위치는 ML Kit OCR bbox로 보강
```

### 4. 품질 평가 로그 추가

추출 품질 비교를 위해 다음 로그를 저장하면 좋다.

```text
page_count
text_length
block_count
line_count
word_count
empty_page_count
avg_block_confidence
symbol_error_candidates
table_like_page_count
extraction_duration_ms
device_memory_delta
battery_delta
```

## 결론

ML Kit 로컬 파싱은 MinerU를 완전히 대체하기에는 부족하다. 하지만 Maestro에서 “서버 추출 실패 시에도 최소한의 텍스트 검색/선택 영역 퀴즈/LLM 질의”를 제공하는 fallback으로는 충분히 가치가 있다.

현재 판단:

```text
MinerU = 문서 구조화/전체 문서 이해용
ML Kit = 로컬 OCR/search/crop fallback용
PDF text layer = born-digital PDF 검색용
별도 extractor = 표/그래프 정밀 데이터용
```

따라서 앞으로는 MinerU와 ML Kit 중 하나를 선택하는 구조보다, 둘을 병렬로 보관하고 기능별로 적절한 source를 고르는 구조가 가장 안전하다.
