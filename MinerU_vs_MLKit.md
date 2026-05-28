# MinerU vs ML Kit 문서 파싱 비교 분석

## 1. 분석 대상

이 문서는 연결된 Android 태블릿의 Maestro 앱 내부 저장소에서 확인한 동일 PDF 문서의 두 가지 파싱 결과를 비교한다.

- 앱 패키지: `com.maestro.app`
- 문서 ID: `289bd432-3344-4075-b6f6-1ddb5adbf9cc`
- PDF 파일명: `ch10.pdf`
- 페이지 수: 60
- 주제: `Chapter 10: Virtual Memory`
- 앱 내부 경로:
  - `files/documents/289bd432-3344-4075-b6f6-1ddb5adbf9cc/content.md`
  - `files/documents/289bd432-3344-4075-b6f6-1ddb5adbf9cc/content.json`
  - `files/documents/289bd432-3344-4075-b6f6-1ddb5adbf9cc/local_mlkit_content.md`
  - `files/documents/289bd432-3344-4075-b6f6-1ddb5adbf9cc/local_mlkit_content.json`

비교 대상은 MinerU가 생성한 `content.md`, `content.json`과 ML Kit 기반 로컬 파서가 생성한 `local_mlkit_content.md`, `local_mlkit_content.json`이다.

## 2. 정량 비교

| 항목 | MinerU | ML Kit |
|---|---:|---:|
| Markdown 크기 | 23,113 bytes | 23,630 bytes |
| JSON 크기 | 78,783 bytes | 2,355,587 bytes |
| 페이지 수 | 60 | 60 |
| Markdown 글자 수 | 22,928 | 23,564 |
| Markdown 비어 있지 않은 줄 수 | 431 | 916 |
| Markdown 단어 수 | 3,476 | 4,311 |
| Markdown 고유 단어 수 | 772 | 840 |
| Markdown heading 수 | 44 | 60 |
| 이미지 참조 수 | 35 | 0 |
| 수식 블록 수 | 24 | 0 |
| HTML table 수 | 0 | 0 |
| JSON 블록 수 | 구조상 직접 재계산 필요 | 856 |
| JSON line 수 | 구조상 직접 재계산 필요 | 1,019 |
| JSON span/word 수 | 구조상 직접 재계산 필요 | 4,149 |

Markdown의 전체 크기는 두 방식이 비슷하지만, JSON 크기는 ML Kit이 훨씬 크다. 이는 ML Kit 결과가 각 페이지의 OCR 블록, 라인, 단어 span, bounding box 좌표를 세밀하게 저장하기 때문이다. 반대로 MinerU JSON은 문서 의미 단위와 콘텐츠 구조를 중심으로 저장되어 더 작고, 문서 이해에 필요한 정보가 더 압축되어 있다.

주의할 점은 기존 앱 내부의 `mineru_mlkit_comparison.md`에서 MinerU의 blocks, lines, spans가 `0`으로 계산되었다는 점이다. 실제 MinerU JSON은 ML Kit과 다른 구조를 사용한다. ML Kit은 `pdf_info -> para_blocks -> lines -> spans` 형태이고, MinerU는 페이지별 블록 리스트 안에 `title`, `paragraph`, `list`, `image`, `equation_interline` 같은 의미 블록이 들어 있다. 따라서 동일한 파서로 집계하면 MinerU 구조를 놓칠 수 있다.

MinerU JSON에서 확인한 주요 블록 유형은 다음과 같다.

| MinerU 블록 유형 | 개수 |
|---|---:|
| `paragraph` | 100 |
| `list` | 72 |
| `page_number` | 58 |
| `title` | 44 |
| `image` | 35 |
| `page_header` | 21 |
| `equation_interline` | 12 |

ML Kit JSON은 모든 블록이 `text` 타입으로 저장되었다.

| ML Kit 블록 유형 | 개수 |
|---|---:|
| `text` | 856 |

## 3. Markdown 결과의 전체 성격

MinerU Markdown은 문서의 의미 구조를 보존하려는 방향이다. 제목은 `#` heading으로 올라오고, 이미지가 있던 위치에는 `![](images/...)` 참조가 들어가며, 수식은 LaTeX에 가까운 형태로 저장된다. 예를 들어 페이지 fault rate 관련 수식은 다음처럼 남는다.

```md
Page Fault Rate $0 \leq p \leq 1$

$$
E A T = (1 - p) \times m e m o r y a c c e s s
$$
```

ML Kit Markdown은 페이지별 OCR 결과에 가깝다. 모든 페이지를 `# Page N`으로 나누고, 해당 페이지에서 인식된 텍스트를 순서대로 나열한다. 이미지나 수식이라는 구조를 따로 이해하지는 않지만, 슬라이드 그림 안에 포함된 작은 라벨이나 로고, 도표 텍스트까지 OCR로 읽으려는 경향이 있다.

```md
# Page 12

in Handling a Page Fault

Steps

page is on backing store

operating system
...
```

결론적으로 MinerU Markdown은 LLM에게 전체 문서 맥락을 제공하기 좋고, ML Kit Markdown은 페이지 단위 검색 또는 화면에 보이는 텍스트를 최대한 긁어오는 용도에 가깝다.

## 4. 차이가 발생한 구체적 사례

### 4.1 표지 페이지: 의미 보존 vs OCR 노이즈

1페이지 표지에서 MinerU는 핵심 제목과 강의 코드만 비교적 간결하게 남겼다.

```text
Chapter 10: Virtual Memory
CSE311
```

ML Kit은 같은 페이지에서 로고와 주변 텍스트까지 OCR로 추출했다.

```text
Chapter 10: Virtual Memo
CSE311
UrIiST
ULSAN NATIONAL INSTITUTE OF SCIENCE AND TECHNOLOGY
200 9
```

여기서 차이가 뚜렷하다.

- MinerU는 `Virtual Memory`를 정확히 보존했다.
- ML Kit은 `Memory`를 `Memo`로 잘못 읽었다.
- ML Kit은 UNIST 로고를 `UrIiST`처럼 오인식했다.
- ML Kit은 페이지 하단 또는 장식 요소로 보이는 숫자까지 `200 9`로 추출했다.

표지처럼 로고, 배경 이미지, 큰 제목이 섞인 페이지에서는 ML Kit이 더 많은 텍스트를 뽑지만, 그만큼 노이즈와 오인식도 늘어난다.

### 4.2 목차 페이지: 텍스트 양은 유사하지만 bullet 인식 차이

2페이지 목차에서 MinerU는 다음처럼 bullet을 `□`에 가깝게 보존했다.

```text
Chapter 10: Virtual-Memory
□ Background
Demand Paging
Copy-on-Write
Page Replacement
Allocation of Frames
Thrashing
Memory-Mapped Files
```

ML Kit은 같은 내용을 다음처럼 읽었다.

```text
Chapter 10: Virtual-Memory
0 Background
O Demand Paging
0 Copy-on-Write
0 Page Replacement
D Allocation of Frames
D Thrashing
D Memory-Mapped Files
```

이 페이지에서는 내용 자체는 거의 비슷하지만, bullet 기호 해석에서 차이가 발생했다. ML Kit은 사각형 bullet을 `0`, `O`, `D`처럼 문자로 오인식했다. 검색에는 큰 문제가 없을 수 있지만, LLM에게 넣었을 때는 불필요한 문자 노이즈가 된다.

### 4.3 그림 중심 페이지: ML Kit은 그림 내부 텍스트를 더 많이 읽음

12페이지 `Steps in Handling a Page Fault`는 도식이 중심인 페이지다. MinerU는 제목 중심으로 간결하게 남겼다.

```text
Steps in Handling a Page Fault
```

반면 ML Kit은 그림 안의 라벨까지 OCR로 추출했다.

```text
in Handling a Page Fault
Steps
page is on backing store
operating system
reference
trap
load M
page table
restart instruction
free frame
reset page table
bring in missing page
physical memory
```

이 경우 ML Kit의 장점이 분명하다. MinerU가 그림을 이미지로 처리하고 내부 텍스트를 충분히 펼치지 못한 반면, ML Kit은 그림 안의 라벨을 텍스트로 검색 가능하게 만들었다. 다만 텍스트 순서가 사람의 읽기 순서와 정확히 일치하지 않고, `load M`, `trap`, `free frame` 같은 라벨들이 문맥 없이 섞이기 때문에 그대로 LLM 문맥으로 넣으면 설명 품질이 흔들릴 수 있다.

### 4.4 복잡한 도표 페이지: ML Kit은 더 많이 뽑지만 순서가 불안정함

17페이지 `Need For Page Replacement`에서도 같은 패턴이 나타난다. MinerU는 슬라이드 제목과 핵심 bullet만 남겼다.

```text
Need For Page Replacement
- Over-allocation of memory
- All memory is in use
```

ML Kit은 페이지 안의 page table, logical memory, physical memory 도식 라벨까지 많이 읽었다.

```text
Need For Page Replacement
valid-invalid bit
monitor
frame
load M
PC
page table for user 1
logical memory for user 1
physical memory
No free frame
page table for user 2
logical memory for user 2
Over-allocation of memory
All memory is in use
```

ML Kit은 도식 내부 텍스트까지 포함하기 때문에 검색 범위는 넓어진다. 그러나 도식의 공간 배치를 문장 구조로 바꾸지는 못한다. 따라서 `monitor`, `frame`, `PC`, `No free frame` 같은 조각 텍스트가 섞이고, 그 관계는 별도로 해석해야 한다.

### 4.5 수식 페이지: MinerU가 수식 표현에 강함

47페이지 `Working-Set Model`에서는 수식과 기호가 많다. MinerU는 다음처럼 LaTeX 형태를 상당히 잘 유지했다.

```text
\square \Delta \equiv working-set window \equiv a fixed number of page references
W S_{i} (working set of Process P_{i}) = set of pages referenced in the most recent \Delta accesses
if \Delta = \infty \Rightarrow \mathrm{WS} will encompass entire program
D = Σ WSSj ≡ total demand frames
```

ML Kit은 같은 내용을 OCR 문자로 읽으면서 일부 기호를 잘못 해석했다.

```text
A= working-set windoW = a fixed number of page references
WS, (working set of Process P) = set of pages referenced in the most recent A accesses
if A = o→WS will encompass entire program
D= £ WSS, = total demand frames
```

대표적인 오인식은 다음과 같다.

| 원래 의미 | MinerU | ML Kit |
|---|---|---|
| 델타 | `\Delta` | `A` |
| 무한대 | `\infty` | `o` |
| 합 기호 | `Σ` | `£` |
| 아래첨자 | `W S_{i}`, `P_{i}` | `WS,`, `P` |

수식, 알고리즘, 운영체제 이론 문서처럼 기호가 중요한 자료에서는 MinerU가 훨씬 안정적이다. ML Kit 결과만 사용하면 수식의 의미가 바뀌거나 손실될 수 있다.

### 4.6 제목 추출과 페이지 구분

MinerU는 문서 의미상 제목으로 판단한 항목을 heading으로 만들었다. 총 heading 수는 44개였다. 반면 ML Kit은 모든 페이지를 `# Page N`으로 구분했기 때문에 heading 수가 60개였다.

이 차이는 용도에 따라 장단점이 나뉜다.

- 문서 요약, 퀴즈 생성, 개념 단위 chunking에는 MinerU heading이 더 유용하다.
- 페이지 단위 검색, 특정 페이지로 이동, OCR 결과 디버깅에는 ML Kit의 `# Page N` 구조가 더 유용하다.

## 5. JSON 구조 차이

### 5.1 MinerU JSON

MinerU JSON은 페이지별 블록 리스트로 구성되어 있고, 각 블록은 의미 타입을 가진다.

예시 구조:

```json
{
  "type": "title",
  "content": {
    "title_content": [
      {
        "type": "text",
        "content": "Chapter 10: Virtual Memory "
      }
    ],
    "level": 1
  },
  "bbox": [137, 325, 861, 405]
}
```

MinerU의 주요 특징은 다음과 같다.

- `title`, `paragraph`, `list`, `image`, `equation_interline` 등 의미 타입이 존재한다.
- 이미지 블록은 이미지 파일 참조로 남는다.
- 수식 블록이 별도 타입으로 분리될 수 있다.
- LLM chunking, 문서 구조화, 개념 추출에 유리하다.
- 다만 그림 내부 라벨을 모두 텍스트로 풀어내지는 못하는 경우가 있다.

### 5.2 ML Kit JSON

ML Kit JSON은 OCR 결과를 페이지, 블록, 라인, span 단위로 저장한다.

예시 구조:

```json
{
  "id": "b0",
  "type": "text",
  "bbox": [256, 448, 1440, 536],
  "text": "Chapter 10: Virtual Memo",
  "lines": [
    {
      "bbox": [256, 448, 1440, 536],
      "text": "Chapter 10: Virtual Memo",
      "spans": [
        {
          "bbox": [256, 448, 628, 536],
          "content": "Chapter"
        }
      ]
    }
  ]
}
```

ML Kit의 주요 특징은 다음과 같다.

- 모든 블록이 `text` 타입이다.
- block, line, span마다 bounding box가 저장된다.
- 좌표 기반 검색, 선택 영역 OCR, crop 기반 추출에 유리하다.
- 그림 내부 텍스트와 작은 라벨까지 잡는 경우가 많다.
- 문서 의미 구조, 수식 구조, 표 구조를 이해하지는 못한다.
- OCR 오인식이 발생하면 의미가 크게 바뀔 수 있다.

## 6. 장단점 분석

### 6.1 MinerU의 장점

MinerU는 문서 이해에 강하다. 이 PDF처럼 강의 슬라이드가 제목, bullet, 그림, 수식으로 구성된 경우 MinerU는 제목과 본문을 비교적 깔끔한 Markdown으로 정리한다. 특히 `Virtual Memory`, `Demand Paging`, `Page Replacement`, `Working-Set Model` 같은 개념 단위가 heading 또는 문단 구조로 드러난다.

수식과 기호 보존도 ML Kit보다 좋다. `\Delta`, `\infty`, `\Rightarrow`, 아래첨자 같은 표현을 LaTeX에 가까운 형태로 남기기 때문에 운영체제, 수학, 알고리즘, 공학 문서에서 의미 손실이 적다.

또한 이미지 위치가 Markdown 안에 `![](images/...)` 형태로 남기 때문에 원문 슬라이드의 시각 자료가 어디에 있었는지 알 수 있다. LLM에게 텍스트 문맥을 넣을 때도 이미지가 들어간 위치를 힌트로 활용할 수 있다.

### 6.2 MinerU의 단점

MinerU는 그림 내부의 작은 텍스트를 항상 충분히 펼쳐주지는 않는다. 12페이지와 17페이지처럼 diagram이 핵심인 슬라이드에서는 제목과 bullet은 잘 남지만, 도식 내부 라벨은 이미지로 남거나 일부만 추출된다. 그래서 페이지 안의 모든 보이는 텍스트를 검색 대상으로 만들고 싶은 경우에는 부족할 수 있다.

또한 결과 JSON 구조가 ML Kit과 다르기 때문에, 현재 앱의 단순 비교기처럼 `pdf_info -> para_blocks -> lines -> spans`만 가정하면 MinerU의 구조 블록을 제대로 세지 못한다. 비교나 인덱싱 로직에서는 MinerU 전용 파서가 필요하다.

### 6.3 ML Kit의 장점

ML Kit은 로컬에서 빠르게 OCR을 수행할 수 있고, 네트워크나 서버 상태에 덜 의존한다. 이 점은 Maestro 앱에서 오프라인 검색, crop capture, lasso 선택 영역 OCR, MinerU 실패 시 fallback에 특히 중요하다.

또한 좌표 정보가 매우 풍부하다. `block`, `line`, `span`마다 bounding box가 있으므로 사용자가 PDF 화면에서 특정 영역을 선택했을 때 해당 영역에 포함된 텍스트를 매칭하기 쉽다. 이는 문서 전체 이해보다는 인터랙티브한 뷰어 기능에 잘 맞는다.

그림 내부 텍스트를 많이 잡는 것도 장점이다. 12페이지의 page fault diagram, 17페이지의 page table diagram, 50페이지의 page-fault frequency 그래프처럼 시각 자료 안에 들어간 라벨을 텍스트화하는 데 MinerU보다 적극적이다.

### 6.4 ML Kit의 단점

ML Kit은 문서 의미 구조를 거의 알지 못한다. 모든 블록이 `text`로 저장되므로 이것이 제목인지, bullet인지, 수식인지, 그림 라벨인지, 페이지 번호인지 구분하기 어렵다.

OCR 오인식도 분명하다. `Memory`를 `Memo`로 읽거나, `UNIST` 로고를 `UrIiST`로 읽고, `\Delta`를 `A`, `Σ`를 `£`, `\infty`를 `o`로 읽는 식의 오류가 있었다. 이런 오류는 단순 검색에는 어느 정도 허용될 수 있지만, 퀴즈 생성이나 개념 설명처럼 의미 정확도가 중요한 작업에서는 문제가 된다.

또한 텍스트 순서가 시각적 배치에 영향을 많이 받는다. 그림 라벨, 축 라벨, 본문 bullet이 한 Markdown 안에 섞이면 사람에게도 LLM에게도 문맥이 흐려질 수 있다.

## 7. 용도별 권장 사용 방식

| 용도 | 권장 파서 | 이유 |
|---|---|---|
| 전체 문서 요약 | MinerU | 제목, 문단, 리스트, 수식 구조가 더 안정적이다. |
| 문서 기반 퀴즈 생성 | MinerU | 개념 단위 문맥이 보존되어 문제 생성 품질이 높다. |
| LLM sidebar의 문서 context | MinerU 우선 | 불필요한 OCR 노이즈가 적고 의미 구조가 좋다. |
| 페이지 내 텍스트 검색 | ML Kit 보조 | 페이지별 OCR과 좌표 정보가 풍부하다. |
| crop/lasso 영역 텍스트 추출 | ML Kit | bounding box 기반 매칭에 유리하다. |
| MinerU 실패 또는 서버 지연 fallback | ML Kit | 로컬에서 동작하므로 가용성이 높다. |
| 수식/기호가 많은 문서 분석 | MinerU | 수식 표현 보존력이 더 좋다. |
| 도식 내부 라벨 검색 | ML Kit 보조 | 그림 안의 작은 텍스트까지 OCR로 잡을 수 있다. |

## 8. 결론

`ch10.pdf` 기준으로 보면 MinerU와 ML Kit은 서로 대체 관계라기보다 보완 관계에 가깝다.

MinerU는 문서를 이해 가능한 지식 구조로 바꾸는 데 강하다. 강의 슬라이드의 제목, bullet, 수식, 이미지 위치를 비교적 잘 보존하므로 LLM 문맥 주입, 요약, 퀴즈 생성, 개념 추출에 적합하다.

ML Kit은 화면에 보이는 텍스트를 좌표와 함께 최대한 OCR하는 데 강하다. 로컬에서 동작하고 좌표 정보가 풍부하므로 검색, crop/lasso 기반 질의, fallback OCR에 적합하다. 다만 수식과 기호, 로고, bullet에 대한 오인식이 많고 문서 구조를 이해하지 못하므로 전체 문서 의미 분석의 주 파서로 쓰기에는 한계가 있다.

따라서 Maestro 앱에서는 다음과 같은 하이브리드 전략이 가장 적합하다.

1. 기본 문서 context와 지식 기반 기능은 MinerU 결과를 우선 사용한다.
2. 페이지 검색, 선택 영역 질의, crop/lasso OCR은 ML Kit 결과를 사용한다.
3. MinerU가 실패했거나 아직 완료되지 않았을 때는 ML Kit Markdown을 fallback context로 사용한다.
4. 두 결과가 모두 있을 때는 MinerU의 의미 구조와 ML Kit의 좌표 기반 OCR을 결합해 페이지 단위 검색/질의 품질을 높인다.
5. 비교 리포트 생성 로직은 MinerU JSON 전용 구조와 ML Kit JSON 전용 구조를 각각 파싱하도록 분리한다.

