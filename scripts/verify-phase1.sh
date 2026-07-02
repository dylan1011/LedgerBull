#!/bin/bash
# =====================================================================
# LedgerBull — Phase 1 Verification Script (Market Data Service ONLY)
# Run this AFTER the coding agent reports Phase 1 complete.
# Run from the project root (the LedgerBull folder).
# The service (market-data-service) should already be running in another terminal.
# =====================================================================

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; }
info() { echo -e "${YELLOW}➤ $1${NC}"; }

echo "======================================================"
echo "  LedgerBull Phase 1 Verification (Market Data Service)"
echo "======================================================"

# --- 1. Containers running ---
info "1. Checking TimescaleDB + Redis containers..."
docker ps --format '{{.Names}}' | grep -q ledgerbull-timescaledb && pass "TimescaleDB container running" || fail "TimescaleDB container NOT running"
docker ps --format '{{.Names}}' | grep -q ledgerbull-redis && pass "Redis container running" || fail "Redis container NOT running"

# --- 2. TimescaleDB extension ---
info "2. Checking TimescaleDB extension..."
docker exec ledgerbull-timescaledb psql -U ledgerbull -d ledgerbull -tAc "SELECT extname FROM pg_extension WHERE extname='timescaledb';" 2>/dev/null | grep -q timescaledb \
  && pass "timescaledb extension active" || fail "timescaledb extension NOT found"

# --- 3. Redis responds ---
info "3. Checking Redis responds..."
[ "$(docker exec ledgerbull-redis redis-cli ping 2>/dev/null)" = "PONG" ] && pass "Redis responds (PONG)" || fail "Redis NOT responding"

# --- 4. Hypertable exists ---
info "4. Checking market_ticks hypertable..."
docker exec ledgerbull-timescaledb psql -U ledgerbull -d ledgerbull -tAc "SELECT hypertable_name FROM timescaledb_information.hypertables;" 2>/dev/null | grep -q market_ticks \
  && pass "market_ticks is a hypertable" || fail "market_ticks hypertable NOT found"

# --- 5. Service health ---
info "5. Checking service health endpoint..."
curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"' && pass "Service health UP" || fail "Service health NOT up (is it running on 8081?)"

# --- 6. Live price + does it CHANGE? (proves real data, not mock) ---
info "6. Checking live price changes over time (proves real feed)..."
P1=$(curl -s http://localhost:8081/api/market-data/latest/BTC-USD)
echo "   first:  $P1"
sleep 6
P2=$(curl -s http://localhost:8081/api/market-data/latest/BTC-USD)
echo "   second: $P2"
if [ -z "$P1" ] || [ -z "$P2" ]; then
  fail "Latest-price endpoint returned nothing"
elif [ "$P1" != "$P2" ]; then
  pass "Price changed between calls → real live data"
else
  echo -e "${YELLOW}⚠  Price identical between calls — could be a quiet market OR mock data. Re-check manually.${NC}"
fi

# --- 7. History endpoint ---
info "7. Checking history endpoint..."
curl -s "http://localhost:8081/api/market-data/history/BTC-USD?minutes=5" | grep -q "BTC-USD" && pass "History endpoint returns ticks" || fail "History endpoint returned no data"

# --- 8. DB persistence + growing? (proves live ingestion) ---
info "8. Checking DB row count grows over time..."
C1=$(docker exec ledgerbull-timescaledb psql -U ledgerbull -d ledgerbull -tAc "SELECT count(*) FROM market_ticks;" 2>/dev/null)
echo "   count now: $C1"
sleep 8
C2=$(docker exec ledgerbull-timescaledb psql -U ledgerbull -d ledgerbull -tAc "SELECT count(*) FROM market_ticks;" 2>/dev/null)
echo "   count +8s: $C2"
if [ -n "$C1" ] && [ -n "$C2" ] && [ "$C2" -gt "$C1" ] 2>/dev/null; then
  pass "Row count increasing → live data persisting"
elif [ -n "$C1" ] && [ "$C1" -gt 0 ] 2>/dev/null; then
  echo -e "${YELLOW}⚠  Rows exist but count didn't grow in 8s — quiet market or ingestion stalled. Re-check.${NC}"
else
  fail "No rows in market_ticks — data is NOT persisting"
fi

# --- 9. Redis cache populated ---
info "9. Checking Redis latest-price cache..."
docker exec ledgerbull-redis redis-cli KEYS "latest:price:*" 2>/dev/null | grep -q "latest:price" && pass "Redis latest-price keys exist" || fail "No latest-price keys in Redis"

# --- 10. Gateway routing ---
info "10. Checking access via API Gateway (8080)..."
curl -s http://localhost:8080/api/market-data/latest/BTC-USD | grep -q . && pass "Reachable via gateway" || fail "NOT reachable via gateway (check route + Eureka registration)"

# --- 11. Anti-fakery: mock/TODO scan ---
info "11. Scanning for mock/placeholder/TODO code..."
HITS=$(grep -rniE "todo|fixme|mock|dummy|placeholder|not implemented|hardcoded" services/market-data-service/src 2>/dev/null)
if [ -z "$HITS" ]; then pass "No mock/TODO/placeholder markers found"; else echo -e "${YELLOW}⚠  Found markers to review:${NC}"; echo "$HITS"; fi

# --- 12. Security: no hardcoded secrets ---
info "12. Scanning for hardcoded secrets..."
SEC=$(grep -rniE "password[[:space:]]*[:=][[:space:]]*[\"'][^\"'$]+[\"']" services/market-data-service/src --include=*.java 2>/dev/null)
if [ -z "$SEC" ]; then pass "No hardcoded secrets in source"; else fail "Possible hardcoded secret — review:"; echo "$SEC"; fi

# --- 13. Git hygiene: no secret files tracked ---
info "13. Checking git for tracked secret files..."
TRK=$(git ls-files | grep -iE '\.env$|secret|credential|\.key$|\.pem$')
if [ -z "$TRK" ]; then pass "No secret files tracked by git"; else fail "Sensitive file tracked — review:"; echo "$TRK"; fi

echo "======================================================"
echo "  Verification complete."
echo "  The two that matter most:"
echo "    #6 (price changes) and #8 (row count grows)"
echo "  If those pass, the core of Phase 1 genuinely works."
echo "======================================================"