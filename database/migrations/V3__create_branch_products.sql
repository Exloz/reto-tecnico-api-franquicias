CREATE TABLE franchise.branch_products (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    stock INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_branch_products_branch
        FOREIGN KEY (branch_id)
        REFERENCES franchise.branches(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_branch_products_stock_non_negative CHECK (stock >= 0),
    CONSTRAINT ck_branch_products_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_branch_products_deleted_after_creation
        CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);
