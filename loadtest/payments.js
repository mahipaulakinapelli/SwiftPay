// SwiftPay — k6 load test
//
// Hits POST /v1/payments on transaction-gateway at a sustained target TPS
// (default 250). Uses constant-arrival-rate executor so the *request rate*,
// not the number of VUs, drives the test — that's the right shape for an
// SLO-style load test of a synchronous HTTP endpoint sitting in front of a
// Kafka-backed pipeline.
//
// Run:
//   k6 run --out json=reports/raw.json loadtest/payments.js
//
// Knobs (env vars):
//   SWIFTPAY_GATEWAY_URL   default http://localhost:8081
//   SWIFTPAY_TARGET_TPS    default 250
//   SWIFTPAY_DURATION      default 3m  (sustained phase)
//   SWIFTPAY_CURRENCY      default USD  (must match a seeded account currency)

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';

const ENDPOINT      = __ENV.SWIFTPAY_GATEWAY_URL || 'http://localhost:8081';
const TARGET_TPS    = parseInt(__ENV.SWIFTPAY_TARGET_TPS || '250', 10);
const DURATION      = __ENV.SWIFTPAY_DURATION || '3m';
const CURRENCY      = __ENV.SWIFTPAY_CURRENCY || 'USD';

// Seed-data: senders MUST have a USD account. Users {1,2,3,5} are USD-eligible
// (user 4 only has INR). Receivers can be anyone — the ledger auto-opens an
// account on demand. Multiple senders deliberately share receivers so the
// ledger's optimistic-locking path is exercised.
const ACCOUNT_PAIRS = [
  [1, 2], [1, 3], [1, 4], [1, 5],
  [2, 1], [2, 3], [2, 4], [2, 5],
  [3, 1], [3, 2], [3, 4], [3, 5],
  [5, 1], [5, 2], [5, 3], [5, 4],
];

// Custom metrics surfaced in the summary report.
const submitDuration = new Trend('payment_submit_duration_ms', true);
const acceptedRate   = new Rate('payment_accepted_rate');
const duplicate409   = new Counter('payment_duplicate_409');
const validation400  = new Counter('payment_validation_400');
const serverError5xx = new Counter('payment_server_error_5xx');

export const options = {
  // One scenario, constant arrival rate, ramp via stages.
  scenarios: {
    sustained_250_tps: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: Math.max(200, TARGET_TPS),
      maxVUs:          Math.max(600, TARGET_TPS * 3),
      stages: [
        // ramping-arrival-rate interpolates between stage targets — to get a true
        // SUSTAINED phase, the stage entering AND the stage doing the sustain must
        // both have `target: TARGET_TPS`.
        { duration: '30s', target: 50 },                          // warm JVM, pools, JIT
        { duration: '30s', target: Math.floor(TARGET_TPS / 2) },  // ramp to half
        { duration: '30s', target: TARGET_TPS },                  // ramp to full
        { duration: DURATION, target: TARGET_TPS },               // SUSTAINED at target
        { duration: '30s', target: 0 },                           // ramp down
      ],
    },
  },

  // Percentile spread + tail. P99 is the one that matters when the outbox
  // relay backs up — the median moves last under contention.
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],

  // Pass/fail gates. Wider than real prod SLOs so transient warm-up doesn't
  // fail the whole run; tighten when you set hard SLOs.
  thresholds: {
    'http_req_failed':                            ['rate<0.01'],
    'http_req_duration{name:POST /v1/payments}':  ['p(95)<800', 'p(99)<1500'],
    'payment_accepted_rate':                      ['rate>0.99'],
    'checks':                                     ['rate>0.99'],
  },
};

export default function () {
  const pair = ACCOUNT_PAIRS[Math.floor(Math.random() * ACCOUNT_PAIRS.length)];
  const payload = JSON.stringify({
    sender_id:      pair[0],
    receiver_id:    pair[1],
    amount:         (Math.random() * 49.99 + 0.01).toFixed(4),
    currency:       CURRENCY,
    transaction_id: uuidv4(),
  });

  const res = http.post(`${ENDPOINT}/v1/payments`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags:    { name: 'POST /v1/payments' },
  });

  submitDuration.add(res.timings.duration);
  acceptedRate.add(res.status === 202);

  if (res.status === 409) duplicate409.add(1);
  if (res.status === 400) validation400.add(1);
  if (res.status >= 500)  serverError5xx.add(1);

  check(res, {
    'status 202':         (r) => r.status === 202,
    'envelope.success':   (r) => r.json('success') === true,
    'data.status PENDING':(r) => r.json('data.status') === 'PENDING',
  });
}

// Two side-effect reports:
//   reports/summary.html — human-readable, paste into the PR
//   reports/summary.json — machine-readable, diff across runs
export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'loadtest/reports/summary.html': htmlReport(data, { title: 'SwiftPay k6 — POST /v1/payments' }),
    'loadtest/reports/summary.json': JSON.stringify(data, null, 2),
  };
}
