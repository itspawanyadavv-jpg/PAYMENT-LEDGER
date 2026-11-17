import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

export const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';

export default function () {
  const params = {
    headers: {
      'X-API-KEY': 'perf-test-key'
    },
    tags: { name: 'GetLedgerEntries' }
  };

  const res = http.get(`${BASE_URL}/api/v1/ledger/entries?limit=50`, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'is array': (r) => Array.isArray(JSON.parse(r.body))
  });

  if (!success) {
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }
}
