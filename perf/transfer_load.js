import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

export const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';

export function setup() {
  const headers = {
    'Content-Type': 'application/json',
    'X-API-KEY': 'perf-test-key'
  };

  const accA = http.post(
    `${BASE_URL}/api/v1/accounts`,
    JSON.stringify({
      accountNumber: `ACC-K6-SRC-${Date.now()}-${Math.floor(Math.random() * 10000)}`,
      ownerName: 'LoadTest Source',
      currency: 'USD',
      openingBalance: '10000000.00'
    }),
    { headers }
  );

  const accB = http.post(
    `${BASE_URL}/api/v1/accounts`,
    JSON.stringify({
      accountNumber: `ACC-K6-DST-${Date.now()}-${Math.floor(Math.random() * 10000)}`,
      ownerName: 'LoadTest Destination',
      currency: 'USD',
      openingBalance: '10000000.00'
    }),
    { headers }
  );

  const srcBody = JSON.parse(accA.body);
  const dstBody = JSON.parse(accB.body);

  return {
    sourceId: srcBody.accountId,
    destId: dstBody.accountId
  };
}

export default function (data) {
  const idempotencyKey = `tx-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  
  // Alternate transfer direction to test bidirectional concurrency
  const aToB = (__ITER % 2 === 0);
  const src = aToB ? data.sourceId : data.destId;
  const dst = aToB ? data.destId : data.sourceId;

  const payload = JSON.stringify({
    idempotencyKey: idempotencyKey,
    sourceAccountId: src,
    destinationAccountId: dst,
    amount: '1.00',
    currency: 'USD'
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY': 'perf-test-key'
    },
    tags: { name: 'PostPaymentTransfer' }
  };

  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, params);

  const success = check(res, {
    'status is 201': (r) => r.status === 201
  });

  if (!success) {
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }
}
