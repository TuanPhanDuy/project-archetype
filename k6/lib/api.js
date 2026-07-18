// Shared helpers for the k6 scenarios. Base URL is overridable: -e BASE_URL=https://...
import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export function health() {
  const res = http.get(`${BASE_URL}/actuator/health`);
  check(res, { 'health 200': (r) => r.status === 200 });
  return res;
}

export function createProduct(name, price) {
  const res = http.post(
    `${BASE_URL}/api/v1/products`,
    JSON.stringify({ name, price }),
    { ...JSON_HEADERS, tags: { name: 'POST /products' } },
  );
  check(res, { 'product 201': (r) => r.status === 201 });
  return res.json('id');
}

export function createOrder(productId, quantity) {
  const body = { customerName: 'k6', items: [{ productId, quantity }] };
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify(body),
    { ...JSON_HEADERS, tags: { name: 'POST /orders' } },
  );
  check(res, { 'order 201': (r) => r.status === 201 });
  return res.json();
}

export function startFulfillment(orderId) {
  const res = http.post(
    `${BASE_URL}/api/v1/orders/${orderId}/fulfillment`,
    null,
    { ...JSON_HEADERS, tags: { name: 'POST /orders/:id/fulfillment' } },
  );
  check(res, { 'fulfillment 202': (r) => r.status === 202 });
  return res.json('id'); // job id
}

export function getJob(jobId) {
  return http.get(`${BASE_URL}/api/v1/jobs/${jobId}`, { tags: { name: 'GET /jobs/:id' } });
}
