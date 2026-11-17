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

  const res = http.post(
    `${BASE_URL}/api/v1/accounts`,
    JSON.stringify({
      accountNumber: `ACC-DEP-${Date.now()}-${Math.floor(Math.random() * 10000)}`,
      ownerName: 'Deposit Load Account',
      currency: 'USD',
      openingBalance: '1000.00'
    }),
    { headers }
  );

  const body = JSON.parse(res.body);
  return { accountId: body.accountId };
}

export default function (data) {
  const idempotencyKey = `dep-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;

  const payload = JSON.stringify({
    amount: '10.00',
    idempotencyKey: idempotencyKey
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY': 'perf-test-key'
    },
    tags: { name: 'PostDeposit' }
  };

  const res = http.post(`${BASE_URL}/api/v1/accounts/${data.accountId}/deposit`, payload, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200
  });

  if (!success) {
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }
}
