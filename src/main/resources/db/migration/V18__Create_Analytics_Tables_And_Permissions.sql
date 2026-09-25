-- ==============================================================================
-- V18: Create Analytics Tables, Projections, RLS Policies, and Permissions
-- ==============================================================================

-- ----------------------------------------------------------------------------
-- 1. Analytics Processed Events (Replay-Safety & Deduplication Log)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics_processed_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_analytics_processed_event UNIQUE (tenant_id, event_id)
);

CREATE INDEX idx_analytics_proc_events_tenant ON analytics_processed_events(tenant_id, event_type);

ALTER TABLE analytics_processed_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON analytics_processed_events
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);


-- ----------------------------------------------------------------------------
-- 2. Daily KPI Snapshots (Materialized Aggregates per Tenant, Unit, Date & Dimension)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics_daily_kpi_snapshots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID REFERENCES units(id),
    date DATE NOT NULL,
    dimension VARCHAR(50) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value NUMERIC(19,4) NOT NULL DEFAULT 0,
    metric_count BIGINT NOT NULL DEFAULT 0,
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_daily_kpi_snapshot UNIQUE (tenant_id, unit_id, date, dimension, metric_name)
);

CREATE INDEX idx_daily_kpi_tenant_date ON analytics_daily_kpi_snapshots(tenant_id, date);
CREATE INDEX idx_daily_kpi_tenant_dim ON analytics_daily_kpi_snapshots(tenant_id, dimension);

ALTER TABLE analytics_daily_kpi_snapshots ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON analytics_daily_kpi_snapshots
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);


-- ----------------------------------------------------------------------------
-- 3. Analytics Work Order Projections (Operational Fast Queries & Metrics)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics_work_order_projections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID REFERENCES units(id),
    work_order_id UUID NOT NULL,
    order_number VARCHAR(50),
    customer_id UUID,
    vehicle_id UUID,
    mechanic_user_id UUID,
    status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    parts_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    services_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    item_count INT NOT NULL DEFAULT 0,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    turnaround_hours NUMERIC(10,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_analytics_wo_projection UNIQUE (tenant_id, work_order_id)
);

CREATE INDEX idx_analytics_wo_tenant_opened ON analytics_work_order_projections(tenant_id, opened_at);
CREATE INDEX idx_analytics_wo_tenant_completed ON analytics_work_order_projections(tenant_id, completed_at);
CREATE INDEX idx_analytics_wo_tenant_status ON analytics_work_order_projections(tenant_id, status);
CREATE INDEX idx_analytics_wo_tenant_mechanic ON analytics_work_order_projections(tenant_id, mechanic_user_id);

ALTER TABLE analytics_work_order_projections ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON analytics_work_order_projections
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);


-- ----------------------------------------------------------------------------
-- 4. Analytics Financial Projections (Cash Flow, Receivables, Payables, Margins)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics_financial_projections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID REFERENCES units(id),
    record_id UUID NOT NULL,
    record_type VARCHAR(50) NOT NULL, -- RECEIVABLE, PAYABLE, TRANSACTION
    category_name VARCHAR(150),
    type VARCHAR(20) NOT NULL, -- CREDIT, DEBIT
    amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL, -- PAID, PENDING, OVERDUE, CANCELED
    due_date DATE,
    paid_date DATE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_analytics_fin_projection UNIQUE (tenant_id, record_id, record_type)
);

CREATE INDEX idx_analytics_fin_tenant_occurred ON analytics_financial_projections(tenant_id, occurred_at);
CREATE INDEX idx_analytics_fin_tenant_due ON analytics_financial_projections(tenant_id, due_date);
CREATE INDEX idx_analytics_fin_tenant_status ON analytics_financial_projections(tenant_id, status);

ALTER TABLE analytics_financial_projections ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON analytics_financial_projections
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);


-- ----------------------------------------------------------------------------
-- 5. Analytics Inventory Projections (Purchases, Consumption, Flow)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics_inventory_projections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID REFERENCES units(id),
    event_ref_id UUID NOT NULL,
    movement_type VARCHAR(50) NOT NULL, -- PURCHASE, WORK_ORDER_CONSUMPTION, TRANSFER, ADJUSTMENT
    product_id UUID,
    product_name VARCHAR(200),
    quantity NUMERIC(12,3) NOT NULL DEFAULT 0,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_value NUMERIC(15,2) NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_analytics_inv_projection UNIQUE (tenant_id, event_ref_id, movement_type)
);

CREATE INDEX idx_analytics_inv_tenant_occurred ON analytics_inventory_projections(tenant_id, occurred_at);
CREATE INDEX idx_analytics_inv_tenant_product ON analytics_inventory_projections(tenant_id, product_id);

ALTER TABLE analytics_inventory_projections ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON analytics_inventory_projections
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);


-- ----------------------------------------------------------------------------
-- 6. Analytics Tool Projections (Custodies, Maintenances, Downtime)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics_tool_projections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID REFERENCES units(id),
    tool_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- MAINTENANCE_SCHEDULED, MAINTENANCE_COMPLETED, CUSTODY_ASSIGNED, CUSTODY_RETURNED
    cost NUMERIC(15,2) NOT NULL DEFAULT 0,
    downtime_hours NUMERIC(10,2) DEFAULT 0,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_analytics_tool_projection UNIQUE (tenant_id, tool_id, event_type, occurred_at)
);

CREATE INDEX idx_analytics_tool_tenant_occurred ON analytics_tool_projections(tenant_id, occurred_at);
CREATE INDEX idx_analytics_tool_tenant_tool ON analytics_tool_projections(tenant_id, tool_id);

ALTER TABLE analytics_tool_projections ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON analytics_tool_projections
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);


-- ----------------------------------------------------------------------------
-- 7. Seed Permissions for Analytics
-- ----------------------------------------------------------------------------
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'ANALYTICS_DASHBOARD_READ', 'ANALYTICS'),
    (uuid_generate_v4(), 'ANALYTICS_REPORT_READ', 'ANALYTICS'),
    (uuid_generate_v4(), 'ANALYTICS_REPORT_EXPORT', 'ANALYTICS'),
    (uuid_generate_v4(), 'ANALYTICS_KPI_READ', 'ANALYTICS')
ON CONFLICT (code) DO NOTHING;

-- Bind permissions to Proprietário and ADMIN roles
DO $$
DECLARE
    prop_role_id UUID;
    admin_role_id UUID;
    perm_rec RECORD;
BEGIN
    FOR prop_role_id IN SELECT id FROM roles WHERE name = 'Proprietário' LOOP
        FOR perm_rec IN SELECT id FROM permissions WHERE module = 'ANALYTICS' LOOP
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (prop_role_id, perm_rec.id)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;

    FOR admin_role_id IN SELECT id FROM roles WHERE name = 'ADMIN' LOOP
        FOR perm_rec IN SELECT id FROM permissions WHERE module = 'ANALYTICS' LOOP
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (admin_role_id, perm_rec.id)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

-- ----------------------------------------------------------------------------
-- 8. Enable MODULE_ANALYTICS in standard Billing Plans
-- ----------------------------------------------------------------------------
INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure)
SELECT id, 'MODULE_ANALYTICS', -1, TRUE, 'BOOLEAN'
FROM billing_plans
WHERE code IN ('TRIAL', 'STARTER', 'PRO', 'ENTERPRISE')
ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = TRUE;
