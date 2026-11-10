// k6 catalog read workload (C90). Read-only: lists and opens public products.
// Run: k6 run -e BASE_URL=http://127.0.0.1:18080 load/catalog.js
import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:18080';

// Load must only reach the isolated local environment.
if (!/^https?:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/.test(BASE_URL)) {
  fail(`BASE_URL must be a local address, got ${BASE_URL}`);
}

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-arrival-rate',
      startRate: 2,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 50,
      stages: [
        { target: 10, duration: '30s' },
        { target: 10, duration: '2m' },
        { target: 0, duration: '10s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:list}': ['p(95)<500'],
    'http_req_duration{name:detail}': ['p(95)<500'],
  },
};

export default function () {
  const list = http.get(`${BASE_URL}/api/v1/products?size=20`, { tags: { name: 'list' } });
  const listed = check(list, { 'list 200': (r) => r.status === 200 });
  if (!listed) return;

  const items = list.json('content') || [];
  if (items.length === 0) return;
  const slug = items[Math.floor(Math.random() * items.length)].slug;
  const detail = http.get(`${BASE_URL}/api/v1/products/${slug}`, { tags: { name: 'detail' } });
  check(detail, { 'detail 200': (r) => r.status === 200 });
}
