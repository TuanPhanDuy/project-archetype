-- Baseline schema. Flyway owns the database; every change ships as a new V<n>__*.sql file.
-- Never edit a migration that has already been applied to a shared environment.

CREATE TABLE product (
    id          UUID            NOT NULL,
    name        VARCHAR(200)    NOT NULL,
    description VARCHAR(2000),
    price       NUMERIC(12, 2)  NOT NULL CHECK (price >= 0),
    created_at  TIMESTAMPTZ     NOT NULL,
    version     BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_product PRIMARY KEY (id)
);

CREATE INDEX idx_product_name ON product (name);
