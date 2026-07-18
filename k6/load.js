// Load test: sustained, expected-peak traffic on the synchronous order path.
// Run: k6 run k6/load.js
import { sleep } from 'k6';
import { createProduct, createOrder } from './lib/api.js';

export const options = {
  scenarios: {
    sync_orders: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },   // ramp up
        { duration: '3m', target: 50 },   // hold at expected peak
        { duration: '1m', target: 0 },    // ramp down
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    'http_req_duration{name:POST /orders}': ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const productId = createProduct(`load-${__VU}-${__ITER}`, 19.99);
  createOrder(productId, 1 + (__ITER % 5));
  sleep(1);
}
