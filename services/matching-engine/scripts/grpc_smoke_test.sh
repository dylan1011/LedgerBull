#!/usr/bin/env bash
# Smoke-test the C++ gRPC matching engine server with grpcurl (no Java).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/build"
SERVER="$BUILD/matching_engine_server"
PORT="${LEDGERBULL_ENGINE_GRPC_PORT:-50051}"
LOG_PATH="$ROOT/data/grpc-smoke-test.log"
SYMBOL="BTC-USD"
SERVICE="ledgerbull.api.MatchingEngine"

RED='\033[0;31m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
pass() { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; exit 1; }
info() { echo -e "${BLUE}➤ $1${NC}"; }

if [[ ! -x "$SERVER" ]]; then
  fail "Server binary not found at $SERVER — build with cmake first"
fi

if ! command -v grpcurl >/dev/null 2>&1; then
  fail "grpcurl not installed (brew install grpcurl)"
fi

rm -f "$LOG_PATH"
mkdir -p "$(dirname "$LOG_PATH")"

info "Starting gRPC server on port $PORT (log: $LOG_PATH)..."
LEDGERBULL_ENGINE_LOG_PATH="$LOG_PATH" \
LEDGERBULL_ENGINE_GRPC_PORT="$PORT" \
  "$SERVER" > /tmp/ledgerbull-grpc-server.log 2>&1 &
SERVER_PID=$!
cleanup() { kill "$SERVER_PID" 2>/dev/null || true; wait "$SERVER_PID" 2>/dev/null || true; }
trap cleanup EXIT

for i in $(seq 1 30); do
  if grpcurl -plaintext "localhost:$PORT" list >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done
grpcurl -plaintext "localhost:$PORT" list | grep -q "$SERVICE" \
  || fail "Server not ready / reflection missing ($SERVICE)"

info "1. Submit resting SELL (id=1, price=105, qty=5)..."
SELL_RESP=$(grpcurl -plaintext -d \
  '{"order_id":"1","symbol":"'"$SYMBOL"'","side":"SELL","type":"LIMIT","price":105,"quantity":5}' \
  "localhost:$PORT" "$SERVICE/SubmitOrder")
echo "$SELL_RESP" | grep -q '"accepted": true' || fail "resting sell not accepted: $SELL_RESP"
echo "$SELL_RESP" | grep -q '"resting_quantity": "5"' || fail "expected resting qty 5: $SELL_RESP"
pass "Resting sell accepted"

info "2. Submit crossing BUY (id=2, price=105, qty=3) — expect fill over gRPC..."
BUY_RESP=$(grpcurl -plaintext -d \
  '{"order_id":"2","symbol":"'"$SYMBOL"'","side":"BUY","type":"LIMIT","price":105,"quantity":3}' \
  "localhost:$PORT" "$SERVICE/SubmitOrder")
echo "$BUY_RESP" | grep -q '"accepted": true' || fail "crossing buy not accepted: $BUY_RESP"
echo "$BUY_RESP" | grep -q '"price": "105"' || fail "fill price missing: $BUY_RESP"
echo "$BUY_RESP" | grep -q '"quantity": "3"' || fail "fill qty missing: $BUY_RESP"
pass "Fill returned over gRPC (price=105 qty=3)"

info "3. QueryBook — expect ask remainder qty=2 at 105..."
BOOK=$(grpcurl -plaintext -d '{"symbol":"'"$SYMBOL"'"}' \
  "localhost:$PORT" "$SERVICE/QueryBook")
echo "$BOOK" | grep -q '"price": "105"' || fail "book missing 105 level: $BOOK"
echo "$BOOK" | grep -q '"quantity": "2"' || fail "book missing qty 2 ask: $BOOK"
pass "Book query shows expected resting ask"

info "4. CancelOrder id=1..."
CANCEL=$(grpcurl -plaintext -d '{"order_id":"1"}' \
  "localhost:$PORT" "$SERVICE/CancelOrder")
echo "$CANCEL" | grep -q '"cancelled": true' || fail "cancel failed: $CANCEL"
pass "Cancel succeeded"

info "5. Crash-recovery — restart server with same log, book state preserved..."
BOOK_BEFORE=$(grpcurl -plaintext -d '{"symbol":"'"$SYMBOL"'"}' \
  "localhost:$PORT" "$SERVICE/QueryBook")

kill "$SERVER_PID" 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true
sleep 0.5

LEDGERBULL_ENGINE_LOG_PATH="$LOG_PATH" \
LEDGERBULL_ENGINE_GRPC_PORT="$PORT" \
  "$SERVER" > /tmp/ledgerbull-grpc-server-2.log 2>&1 &
SERVER_PID=$!

for i in $(seq 1 30); do
  if grpcurl -plaintext "localhost:$PORT" list >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

BOOK2=$(grpcurl -plaintext -d '{"symbol":"'"$SYMBOL"'"}' \
  "localhost:$PORT" "$SERVICE/QueryBook")
if [[ "$BOOK2" != "$BOOK_BEFORE" ]]; then
  fail "book changed after replay (before=$BOOK_BEFORE after=$BOOK2)"
fi
pass "Event log replay via server — book identical after restart"

info "6. Event log contains SUBMIT/CANCEL records..."
grep -q '|SUBMIT|' "$LOG_PATH" || fail "log missing SUBMIT events"
grep -q '|CANCEL|' "$LOG_PATH" || fail "log missing CANCEL events"
pass "Write-ahead event log populated via gRPC path"

echo ""
echo -e "${GREEN}gRPC smoke test PASSED${NC}"
