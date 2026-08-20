-- ==================================================================================
-- V12: Create Inventory Tables, Unit Stocks, Reservations, Transfers, RLS & Permissions
-- ==================================================================================

-- 1. Enhance products table
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(100),
    ADD COLUMN IF NOT EXISTS brand VARCHAR(100),
    ADD COLUMN IF NOT EXISTS unit_of_measure VARCHAR(20) NOT NULL DEFAULT 'UN',
    ADD COLUMN IF NOT EXISTS location_in_warehouse VARCHAR(100),
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_products_tenant_category ON products(tenant_id, category) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_products_tenant_barcode ON products(tenant_id, barcode) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_products_tenant_active ON products(tenant_id, active) WHERE deleted_at IS NULL;

-- 2. Create unit_stocks table
CREATE TABLE IF NOT EXISTS unit_stocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity_on_hand DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    quantity_reserved DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    min_stock DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    max_stock DECIMAL(10, 2),
    shelf_location VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_stocks_tenant_unit_product UNIQUE (tenant_id, unit_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_unit_stocks_tenant_unit ON unit_stocks(tenant_id, unit_id);
CREATE INDEX IF NOT EXISTS idx_unit_stocks_tenant_product ON unit_stocks(tenant_id, product_id);

-- Enable RLS on unit_stocks
ALTER TABLE unit_stocks ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON unit_stocks
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    );

-- 3. Enhance inventory_movements table
ALTER TABLE inventory_movements
    ADD COLUMN IF NOT EXISTS unit_cost_price DECIMAL(10, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS unit_selling_price DECIMAL(10, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_cost_price DECIMAL(12, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS batch_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_movements_idempotency ON inventory_movements(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_movements_tenant_unit ON inventory_movements(tenant_id, unit_id, created_at DESC);

-- Enable RLS on inventory_movements if not already enabled
ALTER TABLE inventory_movements ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_policy ON inventory_movements;
DROP POLICY IF EXISTS tenant_and_unit_isolation_policy ON inventory_movements;

CREATE POLICY tenant_and_unit_isolation_policy ON inventory_movements
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    );

-- 4. Create stock_reservations table
CREATE TABLE IF NOT EXISTS stock_reservations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    product_id UUID NOT NULL REFERENCES products(id),
    work_order_id UUID,
    work_order_item_id UUID,
    quantity DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED', -- CREATED, RELEASED, CONSUMED, CANCELLED
    expires_at TIMESTAMP WITH TIME ZONE,
    released_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_by_user_id UUID,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_reservations_tenant_wo ON stock_reservations(tenant_id, work_order_id);
CREATE INDEX IF NOT EXISTS idx_stock_reservations_tenant_product ON stock_reservations(tenant_id, product_id, status);
CREATE INDEX IF NOT EXISTS idx_stock_reservations_tenant_unit ON stock_reservations(tenant_id, unit_id, status);

-- Enable RLS on stock_reservations
ALTER TABLE stock_reservations ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON stock_reservations
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    );

-- 5. Create stock_transfers table
CREATE TABLE IF NOT EXISTS stock_transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    transfer_number VARCHAR(50) NOT NULL,
    source_unit_id UUID NOT NULL REFERENCES units(id),
    destination_unit_id UUID NOT NULL REFERENCES units(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_TRANSIT, COMPLETED, CANCELLED
    notes TEXT,
    requested_by_user_id UUID,
    received_by_user_id UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    canceled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_tenant_source ON stock_transfers(tenant_id, source_unit_id, status);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_tenant_dest ON stock_transfers(tenant_id, destination_unit_id, status);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_number ON stock_transfers(tenant_id, transfer_number);

-- Enable RLS on stock_transfers
ALTER TABLE stock_transfers ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON stock_transfers
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- 6. Create stock_transfer_items table
CREATE TABLE IF NOT EXISTS stock_transfer_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id UUID NOT NULL REFERENCES stock_transfers(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity DECIMAL(10, 2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_items_transfer ON stock_transfer_items(tenant_id, transfer_id);
CREATE INDEX IF NOT EXISTS idx_stock_transfer_items_product ON stock_transfer_items(tenant_id, product_id);

-- Enable RLS on stock_transfer_items
ALTER TABLE stock_transfer_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON stock_transfer_items
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- 7. Seed system permissions for Inventory
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'INVENTORY_PRODUCT_DELETE', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_STOCK_READ', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_STOCK_WRITE', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_STOCK_ADJUST', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_RESERVATION_READ', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_RESERVATION_WRITE', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_TRANSFER_READ', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_TRANSFER_WRITE', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_MOVEMENT_READ', 'INVENTORY')
ON CONFLICT (code) DO NOTHING;
