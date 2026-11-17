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
      accountNumber: `ACC-LOOKUP-${Date.now()}-${Math.floor(Math.random() * 10000)}`,
      ownerName: 'Lookup Account',
      currency: 'USD',
      openingBalance: '50000.00'
    }),
    { headers }
  );

  const body = JSON.parse(res.body);
  return { accountId: body.accountId };
}

export default function (data) {
  const params = {
    headers: {
      'X-API-KEY': 'perf-test-key'
    },
    tags: { name: 'GetAccountById' }
  };

  const res = http.get(`${BASE_URL}/api/v1/accounts/${data.accountId}`, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'has balance': (r) => JSON.parse(r.body).balance !== undefined
  });

  if (!success) {
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }
}
