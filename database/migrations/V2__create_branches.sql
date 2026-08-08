CREATE TABLE franchise.branches (
    id UUID PRIMARY KEY,
    franchise_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_branches_franchise
        FOREIGN KEY (franchise_id)
        REFERENCES franchise.franchises(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_branches_version_non_negative CHECK (version >= 0)
);
