-- ==================================================================================
-- V13: Create Tools Tables, Categories, Custody Logs, Usages, Maintenance & Permissions
-- ==================================================================================

-- 1. Create tool_categories table
CREATE TABLE IF NOT EXISTS tool_categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    requires_calibration BOOLEAN NOT NULL DEFAULT FALSE,
    default_maintenance_interval_days INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tool_categories_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_tool_categories_tenant ON tool_categories(tenant_id);

ALTER TABLE tool_categories ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON tool_categories
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- 2. Create tools table
CREATE TABLE IF NOT EXISTS tools (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    category_id UUID REFERENCES tool_categories(id) ON DELETE SET NULL,
    asset_tag VARCHAR(100) NOT NULL,
    serial_number VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(100),
    model VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, IN_USE, IN_MAINTENANCE, IN_TRANSIT, DECOMMISSIONED, LOST
    current_holder_user_id UUID,
    location_in_unit VARCHAR(100),
    purchase_date DATE,
    purchase_cost DECIMAL(10, 2),
    last_maintenance_at TIMESTAMP WITH TIME ZONE,
    next_maintenance_due_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tools_tenant_asset_tag ON tools(tenant_id, asset_tag) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tools_tenant_unit_status ON tools(tenant_id, unit_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tools_tenant_category ON tools(tenant_id, category_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tools_tenant_holder ON tools(tenant_id, current_holder_user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tools_maintenance_due ON tools(tenant_id, next_maintenance_due_at) WHERE deleted_at IS NULL;

ALTER TABLE tools ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON tools
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    );

-- 3. Create tool_custody_logs table
CREATE TABLE IF NOT EXISTS tool_custody_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    tool_id UUID NOT NULL REFERENCES tools(id) ON DELETE CASCADE,
    from_user_id UUID,
    to_user_id UUID,
    event_type VARCHAR(50) NOT NULL, -- CHECK_OUT, CHECK_IN, ASSIGN, TRANSFER, RETURN
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_custody_logs_tool ON tool_custody_logs(tenant_id, tool_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tool_custody_logs_user ON tool_custody_logs(tenant_id, to_user_id);

ALTER TABLE tool_custody_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON tool_custody_logs
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    );

-- 4. Create tool_usages table
CREATE TABLE IF NOT EXISTS tool_usages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    tool_id UUID NOT NULL REFERENCES tools(id) ON DELETE CASCADE,
    work_order_id UUID NOT NULL,
    mechanic_user_id UUID,
    checked_out_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_usages_wo ON tool_usages(tenant_id, work_order_id);
CREATE INDEX IF NOT EXISTS idx_tool_usages_tool ON tool_usages(tenant_id, tool_id);

ALTER TABLE tool_usages ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON tool_usages
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    );

-- 5. Create tool_transfers table
CREATE TABLE IF NOT EXISTS tool_transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    transfer_number VARCHAR(50) NOT NULL,
    tool_id UUID NOT NULL REFERENCES tools(id) ON DELETE CASCADE,
    source_unit_id UUID NOT NULL REFERENCES units(id),
    destination_unit_id UUID NOT NULL REFERENCES units(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_TRANSIT, COMPLETED, CANCELLED
    requested_by_user_id UUID,
    received_by_user_id UUID,
    sent_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_transfers_tenant_tool ON tool_transfers(tenant_id, tool_id);
CREATE INDEX IF NOT EXISTS idx_tool_transfers_units ON tool_transfers(tenant_id, source_unit_id, destination_unit_id);

ALTER TABLE tool_transfers ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON tool_transfers
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- 6. Create tool_maintenances table
CREATE TABLE IF NOT EXISTS tool_maintenances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    tool_id UUID NOT NULL REFERENCES tools(id) ON DELETE CASCADE,
    maintenance_type VARCHAR(50) NOT NULL DEFAULT 'PREVENTIVE', -- PREVENTIVE, CORRECTIVE, CALIBRATION, INSPECTION
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    scheduled_date DATE,
    performed_at TIMESTAMP WITH TIME ZONE,
    performed_by_provider VARCHAR(255),
    cost DECIMAL(10, 2),
    description TEXT,
    findings TEXT,
    next_due_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_maintenances_tool ON tool_maintenances(tenant_id, tool_id, scheduled_date DESC);
CREATE INDEX IF NOT EXISTS idx_tool_maintenances_status ON tool_maintenances(tenant_id, status);

ALTER TABLE tool_maintenances ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON tool_maintenances
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (unit_id IS NULL OR NULLIF(current_setting('app.current_unit', true), '') IS NULL OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid)
    );

-- 7. Seed system permissions for Tools Module
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'TOOLS_TOOL_READ', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_TOOL_WRITE', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_TOOL_DELETE', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_CUSTODY_WRITE', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_MAINTENANCE_READ', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_MAINTENANCE_WRITE', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_TRANSFER_READ', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_TRANSFER_WRITE', 'TOOLS'),
    (uuid_generate_v4(), 'TOOLS_CATEGORY_WRITE', 'TOOLS')
ON CONFLICT (code) DO NOTHING;
