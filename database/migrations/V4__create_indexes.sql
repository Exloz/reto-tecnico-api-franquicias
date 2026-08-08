CREATE UNIQUE INDEX uq_franchises_normalized_name
    ON franchise.franchises (normalized_name);

CREATE UNIQUE INDEX uq_branches_franchise_normalized_name
    ON franchise.branches (franchise_id, normalized_name);

CREATE INDEX idx_branches_franchise_order
    ON franchise.branches (franchise_id, normalized_name, id);

CREATE UNIQUE INDEX uq_branch_products_active_normalized_name
    ON franchise.branch_products (branch_id, normalized_name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_branch_products_active_top_stock
    ON franchise.branch_products (branch_id, stock DESC, id)
    WHERE deleted_at IS NULL;
