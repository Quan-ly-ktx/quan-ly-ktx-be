#!/usr/bin/env bash
set -euo pipefail

# KTX Hub Module 4.4 (FR-14) Live API Verification Script
# Covers all 10 endpoints across AdminFacilityExpenseController and AdminFinancialReportController.
# Requires: curl, jq, base64, cmp, head, mktemp
#
# Usage:
#   BASE_URL="http://localhost:8080" \
#   MANAGER_EMAIL="manager@example.com" \
#   MANAGER_PASSWORD="your-password" \
#   ./docs/audits/curl/module-4-4-fr14-verification.sh

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
MANAGER_EMAIL="${MANAGER_EMAIL:-}"
MANAGER_PASSWORD="${MANAGER_PASSWORD:-}"
PERIOD="${PERIOD:-2026-10}"

if [[ -z "$MANAGER_EMAIL" || -z "$MANAGER_PASSWORD" ]]; then
  echo "ERROR: MANAGER_EMAIL and MANAGER_PASSWORD must be set (never hardcode real credentials)." >&2
  exit 1
fi

if [[ ! "$PERIOD" =~ ^[0-9]{4}-(0[1-9]|1[0-2])$ ]]; then
  echo "ERROR: PERIOD must match YYYY-MM (got '$PERIOD')." >&2
  exit 1
fi

for cmd in curl jq base64 cmp head mktemp; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: Required command '$cmd' not found." >&2
    exit 1
  fi
done

TMP_DIR="$(mktemp -d)"
TOKEN=""
ELEC_ID=""
SALARY_ID=""

cleanup() {
  local exit_code=$?
  if [[ -n "$TOKEN" ]]; then
    if [[ -n "$ELEC_ID" ]]; then
      curl -sS -o /dev/null -X DELETE \
        -H "Authorization: Bearer ${TOKEN}" \
        "${BASE_URL}/api/v1/admin/expenses/${ELEC_ID}" || true
    fi
    if [[ -n "$SALARY_ID" ]]; then
      curl -sS -o /dev/null -X DELETE \
        -H "Authorization: Bearer ${TOKEN}" \
        "${BASE_URL}/api/v1/admin/expenses/${SALARY_ID}" || true
    fi
  fi
  rm -rf "$TMP_DIR"
  exit "$exit_code"
}
trap cleanup EXIT

LAST_STATUS=""
LAST_BODY=""

assert_eq() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL [$label]: expected '$expected', got '$actual'" >&2
    if [[ -n "$LAST_BODY" && -f "$LAST_BODY" ]]; then
      echo "Response body:" >&2
      cat "$LAST_BODY" >&2
      echo >&2
    fi
    exit 1
  fi
}

request_json() {
  local label="$1"
  local expected_status="$2"
  local method="$3"
  local path="$4"
  local payload="${5:-}"

  LAST_BODY="${TMP_DIR}/resp-${label}.json"
  local -a args=(
    -sS
    -o "$LAST_BODY"
    -w "%{http_code}"
    -X "$method"
  )
  if [[ -n "$TOKEN" ]]; then
    args+=(-H "Authorization: Bearer ${TOKEN}")
  fi
  if [[ -n "$payload" ]]; then
    args+=(-H "Content-Type: application/json" -d "$payload")
  fi

  LAST_STATUS="$(curl "${args[@]}" "${BASE_URL}${path}")"
  assert_eq "$LAST_STATUS" "$expected_status" "$label"
}

echo "==> 1. Logging in as manager..."
LOGIN_PAYLOAD="$(jq -nc --arg id "$MANAGER_EMAIL" --arg pw "$MANAGER_PASSWORD" '{identifier: $id, password: $pw}')"
request_json "login" "200" "POST" "/api/v1/auth/login" "$LOGIN_PAYLOAD"
TOKEN="$(jq -r '.data.accessToken // empty' "$LAST_BODY")"
if [[ -z "$TOKEN" ]]; then
  echo "FAIL [login]: missing .data.accessToken in response" >&2
  exit 1
fi
echo "    OK (JWT acquired)"

echo "==> 2. Creating ELECTRICITY expense..."
ELEC_PAYLOAD="$(jq -nc --arg d "${PERIOD}-10" '{
  expenseType: "ELECTRICITY",
  title: "Verify EVN Electricity",
  amount: 500000,
  expenseDate: $d,
  invoiceCode: "EVN-VERIFY-01",
  notes: "curl verification"
}')"
request_json "create_electricity" "201" "POST" "/api/v1/admin/expenses" "$ELEC_PAYLOAD"
ELEC_ID="$(jq -r '.data.id // empty' "$LAST_BODY")"
if [[ -z "$ELEC_ID" ]]; then
  echo "FAIL [create_electricity]: missing .data.id" >&2
  exit 1
fi
assert_eq "$(jq -r '.data.expenseType' "$LAST_BODY")" "ELECTRICITY" "create_electricity.expenseType"
echo "    OK (id=$ELEC_ID)"

echo "==> 3. Creating SALARY expense..."
SALARY_PAYLOAD="$(jq -nc --arg d "${PERIOD}-25" '{
  expenseType: "SALARY",
  title: "Verify Staff Salary",
  amount: 3000000,
  expenseDate: $d,
  invoiceCode: "SAL-VERIFY-01",
  notes: "curl verification"
}')"
request_json "create_salary" "201" "POST" "/api/v1/admin/expenses" "$SALARY_PAYLOAD"
SALARY_ID="$(jq -r '.data.id // empty' "$LAST_BODY")"
if [[ -z "$SALARY_ID" ]]; then
  echo "FAIL [create_salary]: missing .data.id" >&2
  exit 1
fi
echo "    OK (id=$SALARY_ID)"

echo "==> 4. Verifying duplicate SALARY in same month returns 409 EXPENSE_409_SALARY_MONTH..."
DUP_SALARY_PAYLOAD="$(jq -nc --arg d "${PERIOD}-26" '{
  expenseType: "SALARY",
  title: "Duplicate Salary",
  amount: 1000000,
  expenseDate: $d
}')"
request_json "duplicate_salary_409" "409" "POST" "/api/v1/admin/expenses" "$DUP_SALARY_PAYLOAD"
assert_eq "$(jq -r '.errorCode' "$LAST_BODY")" "EXPENSE_409_SALARY_MONTH" "duplicate_salary_409.errorCode"
echo "    OK (409 EXPENSE_409_SALARY_MONTH)"

echo "==> 5. Verifying negative amount returns 400 EXPENSE_400_AMOUNT..."
NEG_AMOUNT_PAYLOAD="$(jq -nc --arg d "${PERIOD}-11" '{
  expenseType: "OTHER",
  title: "Negative Amount Check",
  amount: -50000,
  expenseDate: $d
}')"
request_json "negative_amount_400" "400" "POST" "/api/v1/admin/expenses" "$NEG_AMOUNT_PAYLOAD"
assert_eq "$(jq -r '.errorCode' "$LAST_BODY")" "EXPENSE_400_AMOUNT" "negative_amount_400.errorCode"
echo "    OK (400 EXPENSE_400_AMOUNT)"

echo "==> 6. Listing expenses with period, expenseType, and search filters..."
request_json "list_expenses" "200" "GET" "/api/v1/admin/expenses?period=${PERIOD}&expenseType=ELECTRICITY&search=EVN&page=0&size=20"
FOUND_COUNT="$(jq --arg id "$ELEC_ID" '[.data.items[] | select(.id == $id)] | length' "$LAST_BODY")"
assert_eq "$FOUND_COUNT" "1" "list_expenses.contains_created"
echo "    OK"

echo "==> 7. Getting expense detail by ID..."
request_json "get_expense" "200" "GET" "/api/v1/admin/expenses/${ELEC_ID}"
assert_eq "$(jq -r '.data.id' "$LAST_BODY")" "$ELEC_ID" "get_expense.id"
echo "    OK"

echo "==> 8. Updating expense amount and title..."
UPDATE_PAYLOAD="$(jq -nc --arg d "${PERIOD}-10" '{
  expenseType: "ELECTRICITY",
  title: "Verify EVN Electricity Updated",
  amount: 550000,
  expenseDate: $d,
  invoiceCode: "EVN-VERIFY-01",
  notes: "updated via curl"
}')"
request_json "update_expense" "200" "PUT" "/api/v1/admin/expenses/${ELEC_ID}" "$UPDATE_PAYLOAD"
assert_eq "$(jq -r '.data.amount | tonumber | floor | tostring' "$LAST_BODY")" "550000" "update_expense.amount"
assert_eq "$(jq -r '.data.title' "$LAST_BODY")" "Verify EVN Electricity Updated" "update_expense.title"
echo "    OK"

echo "==> 9. Uploading PNG receipt..."
PNG_FILE="${TMP_DIR}/receipt.png"
printf '%s' "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==" | base64 -d > "$PNG_FILE"

LAST_BODY="${TMP_DIR}/resp-upload_receipt.json"
LAST_STATUS="$(curl -sS -o "$LAST_BODY" -w "%{http_code}" \
  -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  -F "file=@${PNG_FILE};type=image/png" \
  "${BASE_URL}/api/v1/admin/expenses/${ELEC_ID}/receipt")"
assert_eq "$LAST_STATUS" "200" "upload_receipt"
assert_eq "$(jq -r '.data.receipt.available' "$LAST_BODY")" "true" "upload_receipt.available"
assert_eq "$(jq -r '.data.receipt.contentUrl' "$LAST_BODY")" "/api/v1/admin/expenses/${ELEC_ID}/receipt/content" "upload_receipt.contentUrl"
echo "    OK"

echo "==> 10. Checking HEAD receipt streaming..."
HEAD_HEADERS="${TMP_DIR}/head-headers.txt"
HEAD_STATUS="$(curl -sS -o /dev/null -D "$HEAD_HEADERS" -w "%{http_code}" \
  -I \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/api/v1/admin/expenses/${ELEC_ID}/receipt/content")"
assert_eq "$HEAD_STATUS" "200" "head_receipt_content"
if ! grep -qi "^content-type:.*image/png" "$HEAD_HEADERS"; then
  echo "FAIL [head_receipt_content]: expected Content-Type image/png" >&2
  cat "$HEAD_HEADERS" >&2
  exit 1
fi
if ! grep -qi "^content-disposition:.*expense-${ELEC_ID}-receipt\.png" "$HEAD_HEADERS"; then
  echo "FAIL [head_receipt_content]: expected inline filename expense-${ELEC_ID}-receipt.png" >&2
  cat "$HEAD_HEADERS" >&2
  exit 1
fi
echo "    OK"

echo "==> 11. Streaming GET receipt bytes and verifying integrity..."
DOWNLOADED_PNG="${TMP_DIR}/downloaded-receipt.png"
GET_RECEIPT_STATUS="$(curl -sS -o "$DOWNLOADED_PNG" -w "%{http_code}" \
  -X GET \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/api/v1/admin/expenses/${ELEC_ID}/receipt/content")"
assert_eq "$GET_RECEIPT_STATUS" "200" "get_receipt_content"
if ! cmp -s "$PNG_FILE" "$DOWNLOADED_PNG"; then
  echo "FAIL [get_receipt_content]: streamed bytes differ from uploaded PNG" >&2
  exit 1
fi
echo "    OK"

echo "==> 12. Querying FR-14 estimated net profit report..."
request_json "net_profit" "200" "GET" "/api/v1/admin/financial-reports/net-profit?period=${PERIOD}"
assert_eq "$(jq -r '.data.period' "$LAST_BODY")" "$PERIOD" "net_profit.period"
assert_eq "$(jq -r '.data.label' "$LAST_BODY")" "Lợi nhuận ròng (ước tính)" "net_profit.label"
assert_eq \
  "$(jq -r '.data.disclaimer' "$LAST_BODY")" \
  "Ước tính dựa trên các khoản thu và chi đã được ghi nhận; có thể chưa bao gồm đầy đủ chi phí, thuế, khấu hao và nghĩa vụ khác." \
  "net_profit.disclaimer"
echo "    OK"

echo "==> 13. Exporting XLSX report and verifying PK magic header..."
XLSX_FILE="${TMP_DIR}/report-${PERIOD}.xlsx"
XLSX_STATUS="$(curl -sS -o "$XLSX_FILE" -w "%{http_code}" \
  -X GET \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/api/v1/admin/financial-reports/net-profit/export?period=${PERIOD}&format=XLSX")"
assert_eq "$XLSX_STATUS" "200" "export_xlsx"
XLSX_MAGIC="$(head -c 2 "$XLSX_FILE" | LC_ALL=C cat)"
assert_eq "$XLSX_MAGIC" "PK" "export_xlsx.magic_header"
echo "    OK"

echo "==> 14. Exporting PDF report and verifying %PDF- magic header..."
PDF_FILE="${TMP_DIR}/report-${PERIOD}.pdf"
PDF_STATUS="$(curl -sS -o "$PDF_FILE" -w "%{http_code}" \
  -X GET \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/api/v1/admin/financial-reports/net-profit/export?period=${PERIOD}&format=PDF")"
assert_eq "$PDF_STATUS" "200" "export_pdf"
PDF_MAGIC="$(head -c 5 "$PDF_FILE" | LC_ALL=C cat)"
assert_eq "$PDF_MAGIC" "%PDF-" "export_pdf.magic_header"
echo "    OK"

echo "==> 15. Deleting receipt..."
request_json "delete_receipt" "200" "DELETE" "/api/v1/admin/expenses/${ELEC_ID}/receipt"
assert_eq "$(jq -r '.data.receipt' "$LAST_BODY")" "null" "delete_receipt.receipt"
echo "    OK"

echo "==> 16. Deleting test expenses..."
request_json "delete_electricity" "200" "DELETE" "/api/v1/admin/expenses/${ELEC_ID}"
ELEC_ID=""
request_json "delete_salary" "200" "DELETE" "/api/v1/admin/expenses/${SALARY_ID}"
SALARY_ID=""
echo "    OK"

echo "ALL MODULE 4.4 (FR-14) LIVE CURL CHECKS PASSED (period=${PERIOD})."
