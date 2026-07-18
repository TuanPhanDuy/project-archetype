// Stress test: push beyond expected peak to find the breaking point and observe recovery.
// Run: k6 run k6/stress.js
import { sleep } from 'k6';
import { createProduct, createOrder } from './lib/api.js';

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '3m', target: 200 },
        { duration: '3m', target: 300 },
        { duration: '2m', target: 0 },   // recovery
      ],
    },
  },
  // Looser thresholds: under stress we expect degradation, but not collapse.
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
  },
};

export default function () {
  const productId = createProduct(`stress-${__VU}-${__ITER}`, 5.0);
  createOrder(productId, 1);
  sleep(0.5);
}
