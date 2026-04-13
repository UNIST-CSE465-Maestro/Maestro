#!/bin/bash
set -e

BASE_URL="https://maestro.jwchae.com"
PDF_FILE="${1:?Usage: ./test.sh <pdf_file>}"

if [ ! -f "$PDF_FILE" ]; then
    echo "ERROR: $PDF_FILE not found"
    exit 1
fi

USER="testuser_$$"
PASS="testpass_$$_Secure1"
EMAIL="${USER}@test.com"

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }
bold()  { printf "\033[1m%s\033[0m\n" "$1"; }

check() {
    local label="$1" code="$2" expect="$3"
    if [ "$code" -eq "$expect" ]; then
        green "  PASS  $label (HTTP $code)"
    else
        red "  FAIL  $label (HTTP $code, expected $expect)"
        exit 1
    fi
}

# ─── 0. Health Check ──────────────────────────────────
bold "[0/5] Health Check"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/health")
check "GET /api/v1/health" "$HTTP_CODE" 200

# ─── 1. Register ──────────────────────────────────────
bold "[1/5] Register ($USER)"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USER\",\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
BODY=$(echo "$RESP" | head -n -1)
HTTP_CODE=$(echo "$RESP" | tail -1)
check "POST /api/v1/auth/register" "$HTTP_CODE" 201
echo "  $BODY"

# ─── 2. Login ────────────────────────────────────────
bold "[2/5] Login"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")
BODY=$(echo "$RESP" | head -n -1)
HTTP_CODE=$(echo "$RESP" | tail -1)
check "POST /api/v1/auth/login" "$HTTP_CODE" 200

ACCESS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['access'])")
REFRESH=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['refresh'])")
echo "  access  = ${ACCESS:0:20}..."
echo "  refresh = ${REFRESH:0:20}..."

# ─── 3. Upload PDF ───────────────────────────────────
bold "[3/5] Upload PDF ($(basename "$PDF_FILE"))"
SHA256=$(sha256sum "$PDF_FILE" | awk '{print $1}')
echo "  sha256 = $SHA256"

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/material-analyzer/" \
    -H "Authorization: Bearer $ACCESS" \
    -F "file=@$PDF_FILE" \
    -F "sha256=$SHA256")
BODY=$(echo "$RESP" | head -n -1)
HTTP_CODE=$(echo "$RESP" | tail -1)
check "POST /api/v1/material-analyzer/" "$HTTP_CODE" 202
echo "  $BODY"

TASK_ID=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "  task_id = $TASK_ID"

# ─── 4. Poll Status ─────────────────────────────────
bold "[4/5] Polling status..."
for i in $(seq 1 60); do
    sleep 5
    RESP=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/v1/material-analyzer/$TASK_ID" \
        -H "Authorization: Bearer $ACCESS")
    BODY=$(echo "$RESP" | head -n -1)
    HTTP_CODE=$(echo "$RESP" | tail -1)
    STATUS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")

    echo "  [$i] status=$STATUS (HTTP $HTTP_CODE)"

    if [ "$STATUS" = "completed" ]; then
        green "  Task completed!"
        break
    elif [ "$STATUS" = "failed" ]; then
        red "  Task failed!"
        echo "  $BODY"
        exit 1
    fi
done

if [ "$STATUS" != "completed" ]; then
    red "  Timeout: task did not complete within 5 minutes"
    exit 1
fi

# ─── 5. Fetch Results ───────────────────────────────
bold "[5/5] Fetch results"

echo "  --- Markdown (first 500 chars) ---"
curl -s "$BASE_URL/api/v1/material-analyzer/${TASK_ID}.md" \
    -H "Authorization: Bearer $ACCESS" | head -c 500
echo ""

echo "  --- JSON (first 500 chars) ---"
curl -s "$BASE_URL/api/v1/material-analyzer/${TASK_ID}.json" \
    -H "Authorization: Bearer $ACCESS" | head -c 500
echo ""

bold ""
green "=== All tests passed ==="
