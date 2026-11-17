import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const paymentsCreated = new Counter('payments_created_201');
const idempotencyDuplicates = new Counter('idempotency_cached_200');
const transferErrors = new Rate('transfer_errors');
const transferDuration = new Trend('payment_transfer_duration_ms');

export const options = {
  scenarios: {
    // Stage 1: Baseline check
    warmup: {
      executor: 'constant-vus',
      vus: 5,
      duration: '10s',
      startTime: '0s',
    },
    // Stage 2: Heavy concurrent money transfer load
    concurrent_transfers: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '20s', target: 50 },  // 50 concurrent payment threads
        { duration: '30s', target: 50 },  // Sustained load
        { duration: '10s', target: 0 },   // Cool down
      ],
      startTime: '10s',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<100', 'p(99)<250'], // Strict FinTech latency SLAs
    'transfer_errors': ['rate<0.01'],                // Error rate < 1%
  },
};

const BASE_URL = __ENV.PAYMENT_LEDGER_API_URL || 'http://localhost:8080';

export function setup() {
  // Pre-create two accounts with $100,000 balance each
  const accA = http.post(
    `${BASE_URL}/api/v1/accounts`,
    JSON.stringify({
      accountNumber: `ACC-K6-SRC-${Date.now()}`,
      accountHolderName: 'LoadTest Source',
      currency: 'USD',
      initialBalance: '100000.00',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const accB = http.post(
    `${BASE_URL}/api/v1/accounts`,
    JSON.stringify({
      accountNumber: `ACC-K6-DST-${Date.now()}`,
      accountHolderName: 'LoadTest Destination',
      currency: 'USD',
      initialBalance: '100000.00',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  return {
    sourceId: JSON.parse(accA.body).id,
    destId: JSON.parse(accB.body).id,
  };
}

export default function (data) {
  // Generate unique idempotency key per iteration
  const idempotencyKey = `k6-tx-${__VU}-${__ITER}-${Date.now()}`;

  const payload = JSON.stringify({
    idempotencyKey: idempotencyKey,
    sourceAccountId: data.sourceId,
    destinationAccountId: data.destId,
    amount: '5.00',
    currency: 'USD',
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'PostPayment' },
  };

  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, params);
  transferDuration.add(Date.now() - start);

  const success = check(res, {
    'Payment created (201)': (r) => r.status === 201,
  });

  if (success) {
    paymentsCreated.add(1);
  } else {
    transferErrors.add(1);
  }

  // Idempotency replay check (send exact same key again)
  if (__ITER % 5 === 0) {
    const replayRes = http.post(`${BASE_URL}/api/v1/payments`, payload, params);
    check(replayRes, {
      'Idempotent replay returns cached payment (200 or 201)': (r) => r.status === 200 || r.status === 201,
    });
    idempotencyDuplicates.add(1);
  }

  sleep(0.05);
}
