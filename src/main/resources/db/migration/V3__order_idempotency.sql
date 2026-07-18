-- Supports safe client retries of POST /api/v1/orders via an Idempotency-Key header:
-- one row per key, remembering which order it produced and a hash of the request body
-- that produced it.

CREATE TABLE idempotency_key (
    key          VARCHAR(255)  NOT NULL,
    request_hash VARCHAR(64)   NOT NULL,
    order_id     UUID          NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (key),
    CONSTRAINT fk_idempotency_key_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_idempotency_key_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_idempotency_key_order ON idempotency_key (order_id);
