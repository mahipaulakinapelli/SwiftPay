#!/usr/bin/env bash
# SwiftPay — orchestrated k6 load test with full network capture.
#
# What it does:
#   1. Starts a tcpdump capture on the loopback interface, filtered to the
#      ports SwiftPay actually uses (HTTP, Kafka external listener, Postgres,
#      Redis). Output goes to loadtest/pcap/swiftpay-load-<timestamp>.pcap.
#   2. Runs the requested k6 scenario.
#   3. Stops tcpdump cleanly, prints the capture size + a one-line summary.
#
# Why:
#   The hackathon submission requires network-level evidence of the end-to-end
#   load test. PCAP shows HTTP requests, Kafka traffic, Postgres queries, and
#   Redis interactions all flowing through the system during the test window.
#
# Usage:
#   loadtest/run-with-pcap.sh                  # default: 5-min payments.js
#   loadtest/run-with-pcap.sh quick            # same as default
#   loadtest/run-with-pcap.sh million          # 1,000,000 txns @ 250 TPS (~67 min)
#
# Requires:
#   - sudo (tcpdump needs BPF access)
#   - k6 on PATH (brew install k6)
#   - SwiftPay stack running (docker compose up -d --build + the JVMs)

set -euo pipefail

SCENARIO="${1:-quick}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PCAP_DIR="$REPO_ROOT/loadtest/pcap"
REPORTS_DIR="$REPO_ROOT/loadtest/reports"

case "$SCENARIO" in
  quick)
    K6_SCRIPT="$REPO_ROOT/loadtest/payments.js"
    SCENARIO_LABEL="5min-quick"
    ;;
  million)
    K6_SCRIPT="$REPO_ROOT/loadtest/payments-million.js"
    SCENARIO_LABEL="1M-txn"
    ;;
  *)
    echo "Unknown scenario: $SCENARIO  (use 'quick' or 'million')" >&2
    exit 2
    ;;
esac

mkdir -p "$PCAP_DIR" "$REPORTS_DIR"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
PCAP_FILE="$PCAP_DIR/swiftpay-load-${SCENARIO_LABEL}-${TIMESTAMP}.pcap"

# Ports SwiftPay traffic flows through on the host loopback:
#   8081 transaction-gateway HTTP
#   8082 ledger-service     HTTP
#   8083 analytics-worker   HTTP
#  29092 Kafka external listener (docker-compose maps it on the host)
#   5432 PostgreSQL
#   6379 Redis
#  (8080 Kafka UI traffic is omitted — not interesting for the submission)
BPF_FILTER='port 8081 or port 8082 or port 8083 or port 29092 or port 5432 or port 6379'

echo "═══════════════════════════════════════════════════════════════════════"
echo "  SwiftPay load run + PCAP capture"
echo "═══════════════════════════════════════════════════════════════════════"
echo "  Scenario: $SCENARIO_LABEL"
echo "  k6 script: $K6_SCRIPT"
echo "  PCAP file: $PCAP_FILE"
echo "  BPF filter: $BPF_FILTER"
echo

# Sanity: gateway must be up before we start.
if ! curl -fsS http://localhost:8081/actuator/health >/dev/null 2>&1; then
  echo "✘  transaction-gateway is not responding on :8081 — bring up the stack first." >&2
  exit 3
fi

# tcpdump needs root for BPF on macOS. Use sudo and request a password if needed.
# -i lo0      capture the loopback (all SwiftPay traffic is local in dev)
# -s 0        no snap length cap — capture full packets so payloads are visible
# -B 32768    larger kernel buffer to avoid drops at 250 TPS
# -U          unbuffered packet write (so the file is usable mid-capture)
# -w <file>   write to pcap file
echo "→ Starting tcpdump (will prompt for sudo password)…"
# Non-interactive invocations (e.g. CI) can pre-pipe a password via the
# SWIFTPAY_SUDO_PASSWORD env var. Real-terminal users just type their password
# at the prompt.
if [[ -n "${SWIFTPAY_SUDO_PASSWORD:-}" ]]; then
  echo "$SWIFTPAY_SUDO_PASSWORD" | sudo -S tcpdump -i lo0 -s 0 -B 32768 -U \
       -w "$PCAP_FILE" "$BPF_FILTER" >/dev/null 2>"$PCAP_DIR/.tcpdump.stderr" &
else
  sudo tcpdump -i lo0 -s 0 -B 32768 -U -w "$PCAP_FILE" "$BPF_FILTER" \
       >/dev/null 2>"$PCAP_DIR/.tcpdump.stderr" &
fi
TCPDUMP_PID=$!

# Make sure we stop tcpdump even if k6 fails or this script is killed. We
# pkill -2 the actual tcpdump binary by PID-of-parent so we don't have to
# escalate the kill through sudo and rely on signal forwarding (which is flaky).
cleanup() {
  echo "→ Stopping tcpdump…"
  if [[ -n "${SWIFTPAY_SUDO_PASSWORD:-}" ]]; then
    echo "$SWIFTPAY_SUDO_PASSWORD" | sudo -S pkill -INT -P "$TCPDUMP_PID" tcpdump 2>/dev/null || true
    echo "$SWIFTPAY_SUDO_PASSWORD" | sudo -S pkill -INT -x tcpdump 2>/dev/null || true
  else
    sudo pkill -INT -P "$TCPDUMP_PID" tcpdump 2>/dev/null || true
    sudo pkill -INT -x tcpdump 2>/dev/null || true
  fi
  wait "$TCPDUMP_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Give tcpdump a moment to start listening so we don't miss the first packets.
sleep 2

echo "→ Running k6 ($K6_SCRIPT)…"
echo
k6 run "$K6_SCRIPT"
K6_EXIT=$?

cleanup
trap - EXIT INT TERM

echo
echo "═══════════════════════════════════════════════════════════════════════"
echo "  Capture complete"
echo "═══════════════════════════════════════════════════════════════════════"
ls -lh "$PCAP_FILE" | awk '{print "  PCAP:   " $9 "  (" $5 ")"}'

# Quick PCAP sanity stats — packet count + per-port breakdown.
if [[ -f "$PCAP_FILE" ]]; then
  PACKET_COUNT=$(tcpdump -r "$PCAP_FILE" -nn 2>/dev/null | wc -l | tr -d ' ' || echo "?")
  echo "  Packets captured: $PACKET_COUNT"
  echo "  By port:"
  for port in 8081 8082 8083 29092 5432 6379; do
    n=$(tcpdump -r "$PCAP_FILE" -nn "port $port" 2>/dev/null | wc -l | tr -d ' ')
    echo "    :$port → $n packets"
  done
else
  echo "  ✘ PCAP file missing — tcpdump never wrote it (check $PCAP_DIR/.tcpdump.stderr)"
fi
echo "  k6 reports:       $REPORTS_DIR/"

exit "$K6_EXIT"
