-- ==================================================================================
-- V11: Enhance Work Orders, Create Work Order Items Table, Indexes, RLS & Permissions
-- ==================================================================================

-- 1. Enhance work_orders table
ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS customer_id UUID REFERENCES customers(id),
    ADD COLUMN IF NOT EXISTS order_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS service_bay VARCHAR(50),
    ADD COLUMN IF NOT EXISTS start_mileage INTEGER,
    ADD COLUMN IF NOT EXISTS end_mileage INTEGER,
    ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_services_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_parts_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS diagnosis_notes TEXT,
    ADD COLUMN IF NOT EXISTS customer_notes TEXT,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

-- Partial indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_work_orders_tenant_customer ON work_orders(tenant_id, customer_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_orders_tenant_vehicle ON work_orders(tenant_id, vehicle_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_orders_tenant_quote ON work_orders(tenant_id, quote_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_orders_tenant_mechanic ON work_orders(tenant_id, mechanic_user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_orders_tenant_unit_status ON work_orders(tenant_id, unit_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_orders_order_number ON work_orders(tenant_id, order_number) WHERE deleted_at IS NULL;

-- 2. Create work_order_items table
CREATE TABLE IF NOT EXISTS work_order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    work_order_id UUID NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    type VARCHAR(50) NOT NULL, -- PART / SERVICE
    product_id UUID REFERENCES products(id),
    assigned_mechanic_id UUID REFERENCES users(id),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING / IN_PROGRESS / COMPLETED
    quantity DECIMAL(10, 2) NOT NULL DEFAULT 1.00,
    unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    tax_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_work_order_items_order ON work_order_items(tenant_id, work_order_id);
CREATE INDEX IF NOT EXISTS idx_work_order_items_product ON work_order_items(tenant_id, product_id);

-- 3. Enable RLS on work_order_items
ALTER TABLE work_order_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON work_order_items
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- 4. Seed system permissions for Work Orders
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'OPERATIONS_ORDER_READ', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_ORDER_WRITE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_ORDER_EXECUTE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_ORDER_CLOSE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_ORDER_CANCEL', 'OPERATIONS')
ON CONFLICT (code) DO NOTHING;
