-- ==============================================================================
-- V10: Enhance Quotes and Create Quote Items Table, Indexes, RLS and Permissions
-- ==============================================================================

-- 1. Enhance quotes table with customer, associations, approval workflow and monetary breakdown
ALTER TABLE quotes
    ADD COLUMN IF NOT EXISTS customer_id UUID REFERENCES customers(id),
    ADD COLUMN IF NOT EXISTS inspection_id UUID REFERENCES inspections(id),
    ADD COLUMN IF NOT EXISTS appointment_id UUID REFERENCES appointments(id),
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_by_user_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS customer_approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS customer_decision_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS customer_decision_notes TEXT,
    ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_labor_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_parts_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS terms_and_conditions TEXT,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_quotes_tenant_customer ON quotes(tenant_id, customer_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_quotes_tenant_vehicle ON quotes(tenant_id, vehicle_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_quotes_tenant_inspection ON quotes(tenant_id, inspection_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_quotes_tenant_unit ON quotes(tenant_id, unit_id) WHERE deleted_at IS NULL;

-- 2. Create quote_items table
CREATE TABLE IF NOT EXISTS quote_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    quote_id UUID NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    type VARCHAR(50) NOT NULL, -- PART / LABOR
    product_id UUID REFERENCES products(id),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    quantity DECIMAL(10, 2) NOT NULL DEFAULT 1.00,
    unit_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    tax_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quote_items_tenant_quote ON quote_items(tenant_id, quote_id);

-- 3. Enable Row Level Security (RLS) on quote_items
ALTER TABLE quote_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON quote_items
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- 4. Seed System Permissions for Operations Quotes
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'OPERATIONS_QUOTE_READ', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_QUOTE_WRITE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_QUOTE_APPROVE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_QUOTE_SEND', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_QUOTE_CANCEL', 'OPERATIONS')
ON CONFLICT (code) DO NOTHING;
