-- Adds catalog categories, the order aggregate, and an async job tracker.
-- Forward-only: extends V1 without rewriting it.

-- ===== Catalog =====
CREATE TABLE category (
    id          UUID         NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_name UNIQUE (name)
);

-- Product now belongs to a category. Nullable so existing rows stay valid.
ALTER TABLE product ADD COLUMN category_id UUID;
ALTER TABLE product ADD CONSTRAINT fk_product_category
    FOREIGN KEY (category_id) REFERENCES category (id);
CREATE INDEX idx_product_category ON product (category_id);

-- ===== Orders ===== ("order" is reserved in SQL, so the table is "orders")
CREATE TABLE orders (
    id            UUID          NOT NULL,
    customer_name VARCHAR(200)  NOT NULL,
    status        VARCHAR(32)   NOT NULL,
    total_amount  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE order_item (
    id         UUID           NOT NULL,
    order_id   UUID           NOT NULL,
    product_id UUID           NOT NULL,
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);
CREATE INDEX idx_order_item_order ON order_item (order_id);
CREATE INDEX idx_order_item_product ON order_item (product_id);

-- ===== Async processing jobs =====
-- Generic tracker for asynchronous work: clients poll it for status.
CREATE TABLE processing_job (
    id           UUID          NOT NULL,
    type         VARCHAR(64)   NOT NULL,
    status       VARCHAR(32)   NOT NULL,
    reference_id UUID,
    result       VARCHAR(2000),
    error        VARCHAR(2000),
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_processing_job PRIMARY KEY (id)
);
CREATE INDEX idx_processing_job_status ON processing_job (status);
