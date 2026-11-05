#!/usr/bin/env sh
# Smoke test of a running Embedding Explorer 3D stack, through nginx like a browser.
#   ./scripts/smoke-test.sh            (uses VIZ_HTTP_PORT from the environment or 8088)
#   ./scripts/smoke-test.sh http://localhost:8088
# Exit code 0 when every check passes.

set -u

BASE_URL="${1:-http://127.0.0.1:${VIZ_HTTP_PORT:-8088}}"
WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-120}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM

failures=0

pass() { printf '  \033[32mOK\033[0m    %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; failures=$((failures + 1)); }
info() { printf '        %s\n' "$1"; }

# Prints the HTTP status; the body goes to the given file.
fetch() {
  curl -s -o "$2" -w '%{http_code}' --max-time 90 "$BASE_URL$1" 2>/dev/null || printf '000'
}

# Extracts a JSON string or number field with sed (no jq needed); first match only.
field() {
  sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" "$2" | head -n 1
}

printf 'Embedding Explorer 3D – smoke test on %s\n\n' "$BASE_URL"

printf 'Waiting for the frontend (up to %ss)...\n' "$WAIT_SECONDS"
elapsed=0
until [ "$(fetch /healthz "$TMP_DIR/healthz")" = "200" ]; do
  if [ "$elapsed" -ge "$WAIT_SECONDS" ]; then
    fail "the frontend does not answer at $BASE_URL (docker compose ps, docker compose logs viz-frontend)"
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

printf '\n1. Frontend\n'
status="$(fetch / "$TMP_DIR/index.html")"
if [ "$status" = "200" ] && grep -q 'Embedding Explorer 3D' "$TMP_DIR/index.html"; then
  pass "the page is served"
else
  fail "the page answered HTTP $status"
fi
headers="$(curl -s -D - -o /dev/null --max-time 10 "$BASE_URL/")"
if printf '%s' "$headers" | grep -qi "content-security-policy: default-src 'self'"; then
  pass "security headers are present (Content-Security-Policy)"
else
  fail "the Content-Security-Policy header is missing"
fi

printf '\n2. Backend through the /api/ proxy\n'
status="$(fetch /api/v1/diagnostics "$TMP_DIR/diagnostics.json")"
if [ "$status" = "200" ]; then
  overall="$(sed -n 's/^{"status":"\([A-Z]*\)".*/\1/p' "$TMP_DIR/diagnostics.json")"
  pass "diagnostics answered (overall status: ${overall:-?})"
  # One line per check: id, status and code.
  tr '{' '\n' < "$TMP_DIR/diagnostics.json" | grep '"id":"' | grep '"status":"' | while read -r check; do
    id="$(printf '%s' "$check" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
    st="$(printf '%s' "$check" | sed -n 's/.*"status":"\([A-Z]*\)".*/\1/p')"
    code="$(printf '%s' "$check" | sed -n 's/.*"code":"\([A-Z_]*\)".*/\1/p')"
    info "$(printf '%-22s %-8s %s' "$id" "$st" "$code")"
  done
else
  fail "diagnostics answered HTTP $status (is viz-backend healthy? docker compose ps)"
fi

printf '\n3. Points of the 3D view\n'
status="$(fetch /api/v1/points "$TMP_DIR/points.json")"
if [ "$status" = "200" ]; then
  count="$(grep -o '"preview":' "$TMP_DIR/points.json" | wc -l | tr -d ' ')"
  version="$(field datasetVersion "$TMP_DIR/points.json")"
  explained="$(field totalExplainedVariance "$TMP_DIR/points.json")"
  pass "$count points, datasetVersion $version, explained variance $explained"
else
  code="$(field code "$TMP_DIR/points.json")"
  fail "points answered HTTP $status ${code:+(code $code)} – see the \"Stato\" section in the UI"
fi

printf '\n'
if [ "$failures" -eq 0 ]; then
  printf '\033[32mAll checks passed.\033[0m Open %s\n' "$BASE_URL"
  exit 0
fi
printf '\033[31m%s check(s) failed.\033[0m\n' "$failures"
exit 1
