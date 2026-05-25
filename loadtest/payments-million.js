// SwiftPay — k6 long-run scenario
//
// Drives 1,000,000 payments at sustained 250 TPS for the hackathon submission
// load-test evidence. Mirrors `payments.js` exactly for request shape, custom
// metrics, and reports — only the scenario stages change.
//
// 1,000,000 / 250 TPS = 4000 seconds of sustained traffic
// Plus warm-up + ramp + ramp-down stages = ~4200s total (~70 min).
//
// Run with PCAP capture via:   loadtest/run-with-pcap.sh million
// Or standalone:               k6 run loadtest/payments-million.js
//
// Knobs (env vars):
//   SWIFTPAY_GATEWAY_URL   default http://localhost:8081
//   SWIFTPAY_TARGET_TPS    default 250
//   SWIFTPAY_CURRENCY      default USD

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';

const ENDPOINT      = __ENV.SWIFTPAY_GATEWAY_URL || 'http://localhost:8081';
const TARGET_TPS    = parseInt(__ENV.SWIFTPAY_TARGET_TPS || '250', 10);
const CURRENCY      = __ENV.SWIFTPAY_CURRENCY || 'USD';

// 1,000,000 / TARGET_TPS rounded up to the nearest second. At 250 TPS that's
// 4000 seconds (66:40 min).
const SUSTAINED_SECONDS = Math.ceil(1_000_000 / TARGET_TPS);

const ACCOUNT_PAIRS = [
  [1, 2], [1, 3], [1, 4], [1, 5],
  [2, 1], [2, 3], [2, 4], [2, 5],
  [3, 1], [3, 2], [3, 4], [3, 5],
  [5, 1], [5, 2], [5, 3], [5, 4],
];

const submitDuration = new Trend('payment_submit_duration_ms', true);
const acceptedRate   = new Rate('payment_accepted_rate');
const duplicate409   = new Counter('payment_duplicate_409');
const validation400  = new Counter('payment_validation_400');
const serverError5xx = new Counter('payment_server_error_5xx');

export const options = {
  scenarios: {
    one_million_at_250_tps: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      // Headroom for a 67-minute run — keep up even if a few VUs are slow.
      preAllocatedVUs: Math.max(300, TARGET_TPS),
      maxVUs:          Math.max(800, TARGET_TPS * 4),
      stages: [
        { duration: '30s',                              target: 50 },                          // warm
        { duration: '30s',                              target: Math.floor(TARGET_TPS / 2) },  // ramp half
        { duration: '30s',                              target: TARGET_TPS },                  // ramp to full
        { duration: `${SUSTAINED_SECONDS}s`,            target: TARGET_TPS },                  // SUSTAINED — 1M transactions
        { duration: '30s',                              target: 0 },                           // ramp down
      ],
    },
  },

  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],

  // Looser tail-latency gates than the 5-min run — a 67-minute run will see
  // GC ticks, log rotation, etc. Failures and accept-rate gates stay tight.
  thresholds: {
    'http_req_failed':                            ['rate<0.01'],
    'http_req_duration{name:POST /v1/payments}':  ['p(95)<1500', 'p(99)<3000'],
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

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'loadtest/reports/summary-million.html': htmlReport(data, { title: 'SwiftPay k6 — 1M txns @ 250 TPS' }),
    'loadtest/reports/summary-million.json': JSON.stringify(data, null, 2),
  };
}
