# Performance tests (k6)

Load/performance tests written for [k6](https://k6.io). Each script targets the running
service over HTTP and enforces **thresholds** (pass/fail criteria) so they double as
performance gates in CI.

## Install

```bash
brew install k6           # macOS
# or: https://grafana.com/docs/k6/latest/set-up/install-k6/
```

## Run

Start the app (and its Postgres) first, then point a script at it:

```bash
# against localhost
k6 run k6/smoke.js

# against another environment
k6 run -e BASE_URL=https://staging.example.com k6/load.js
```

`BASE_URL` defaults to `http://localhost:8080`.

## Scenarios

| Script                  | Purpose                                          | Shape                          |
|-------------------------|--------------------------------------------------|--------------------------------|
| `smoke.js`              | Sanity + baseline latency                        | 1 VU, 30s                      |
| `load.js`               | Sustained expected-peak on the **sync** order API| ramp to 50 VUs, hold 3m        |
| `stress.js`             | Find the breaking point + recovery               | ramp to 300 VUs                |
| `async-fulfillment.js`  | The **async** 202 + poll flow; end-to-end latency| ramp to 20 VUs, custom trend   |

`lib/api.js` holds the shared request helpers (create product/order, start fulfillment,
poll job) and tags requests so per-endpoint latencies show up separately in the summary.

## Thresholds (tune to your SLOs)

- `http_req_failed: rate<0.01` — under 1% errors (looser under stress).
- `http_req_duration: p(95)<...` — latency budget; mirrors the SLOs in
  `management.metrics.distribution.slo` so k6 and Prometheus agree.
- `fulfillment_duration_ms: p(95)<5000` — async kick-off → COMPLETED.

A non-zero exit means a threshold was breached — wire that into CI to fail the build.

## Tip: watch it live

Run the [observability stack](../observability) and the app in parallel, then watch
`http_server_requests` and `orders_placed_total` move in Grafana while k6 drives traffic.
