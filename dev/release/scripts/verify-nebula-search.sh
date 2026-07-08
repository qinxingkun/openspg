#!/usr/bin/env bash
# Smoke-test OpenSPG Nebula search APIs against a running server (default http://127.0.0.1:8887).
set -euo pipefail

BASE_URL="${OPENSPG_BASE_URL:-http://127.0.0.1:8887}"
PROJECT_ID="${OPENSPG_PROJECT_ID:-1}"
KEYWORD="${OPENSPG_SEARCH_KEYWORD:-吴边}"

urlencode() {
  python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "$1"
}

ENC_KEYWORD="$(urlencode "${KEYWORD}")"

echo "== spgType =="
curl -sS "${BASE_URL}/public/v1/search/spgType?projectId=${PROJECT_ID}&keyword=${ENC_KEYWORD}&pageIdx=0&pageSize=5"
echo

echo "== text =="
curl -sS -X POST "${BASE_URL}/public/v1/search/text" \
  -H 'Content-Type: application/json' \
  -d "$(python3 - <<PY
import json
print(json.dumps({
  "projectId": int("${PROJECT_ID}"),
  "queryString": "${KEYWORD}",
  "labelConstraints": ["SmokeTest.Scholar"],
  "page": 1,
  "topk": 5,
}))
PY
)"
echo

echo "== custom (LOOKUP sample) =="
curl -sS -X POST "${BASE_URL}/public/v1/search/custom" \
  -H 'Content-Type: application/json' \
  -d "$(python3 - <<'PY'
import json
print(json.dumps({
  "projectId": 1,
  "customQuery": "LOOKUP ON `SmokeTest_Scholar` YIELD vertex AS v | LIMIT 3",
}))
PY
)"
echo
