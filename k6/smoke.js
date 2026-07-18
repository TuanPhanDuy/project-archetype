// Smoke test: minimal load, just confirm the system works and basic latency is sane.
// Run: k6 run k6/smoke.js   (or: BASE_URL=... k6 run -e BASE_URL=$BASE_URL k6/smoke.js)
import { sleep } from 'k6';
import { health, createProduct, createOrder } from './lib/api.js';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],     // <1% errors
    http_req_duration: ['p(95)<500'],   // 95% under 500ms
    checks: ['rate>0.99'],
  },
};

export default function () {
  health();
  const productId = createProduct(`smoke-${__VU}-${__ITER}`, 9.99);
  createOrder(productId, 2);
  sleep(1);
}
