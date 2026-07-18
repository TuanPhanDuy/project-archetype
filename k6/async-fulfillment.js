// Async-path test: exercise the 202 + poll-the-job pattern and measure end-to-end
// fulfillment latency (kick-off → COMPLETED) as a custom trend.
// Run: k6 run k6/async-fulfillment.js
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { createProduct, createOrder, startFulfillment, getJob } from './lib/api.js';

const fulfillmentDuration = new Trend('fulfillment_duration_ms', true);
const fulfillmentSuccess = new Rate('fulfillment_success');

export const options = {
  scenarios: {
    async_fulfillment: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    fulfillment_duration_ms: ['p(95)<5000'],
    fulfillment_success: ['rate>0.99'],
  },
};

export default function () {
  const productId = createProduct(`async-${__VU}-${__ITER}`, 5.0);
  const order = createOrder(productId, 1);
  const jobId = startFulfillment(order.id);

  const start = Date.now();
  let status = 'PENDING';
  for (let i = 0; i < 50; i++) {
    status = getJob(jobId).json('status');
    if (status === 'COMPLETED' || status === 'FAILED') break;
    sleep(0.2);
  }
  fulfillmentDuration.add(Date.now() - start);
  fulfillmentSuccess.add(status === 'COMPLETED');
  check(status, { 'fulfillment completed': (s) => s === 'COMPLETED' });
}
