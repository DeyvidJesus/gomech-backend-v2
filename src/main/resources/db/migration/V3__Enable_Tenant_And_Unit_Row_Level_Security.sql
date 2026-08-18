-- ============================================================================
-- V3: Enable PostgreSQL Row-Level Security (RLS) for Tenant and Unit Isolation
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Tenants (Root Company Account)
-- ----------------------------------------------------------------------------
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON tenants
    FOR ALL
    USING (id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- ----------------------------------------------------------------------------
-- 2. Units (Workshop Branches)
-- ----------------------------------------------------------------------------
ALTER TABLE units ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON units
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    );

-- ----------------------------------------------------------------------------
-- 3. Tenant-Scoped Core Entities (users, roles, user_roles)
-- ----------------------------------------------------------------------------
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON users
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON roles
    FOR ALL
    USING (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON user_roles
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    );

-- ----------------------------------------------------------------------------
-- 4. CRM (customers, vehicles)
-- ----------------------------------------------------------------------------
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON customers
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE vehicles ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON vehicles
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- ----------------------------------------------------------------------------
-- 5. Inventory (suppliers, products, inventory_movements)
-- ----------------------------------------------------------------------------
ALTER TABLE suppliers ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON suppliers
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE products ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON products
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    );

ALTER TABLE inventory_movements ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON inventory_movements
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    );

-- ----------------------------------------------------------------------------
-- 6. Operations (quotes, work_orders)
-- ----------------------------------------------------------------------------
ALTER TABLE quotes ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON quotes
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    );

ALTER TABLE work_orders ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON work_orders
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    );

-- ----------------------------------------------------------------------------
-- 7. Finance & Billing (subscriptions, financial_transactions)
-- ----------------------------------------------------------------------------
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON subscriptions
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE financial_transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_and_unit_isolation_policy ON financial_transactions
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR NULLIF(current_setting('app.current_unit', true), '') IS NULL
            OR unit_id = NULLIF(current_setting('app.current_unit', true), '')::uuid
        )
    );

-- ----------------------------------------------------------------------------
-- 8. Audit Logs
-- ----------------------------------------------------------------------------
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON audit_logs
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);
