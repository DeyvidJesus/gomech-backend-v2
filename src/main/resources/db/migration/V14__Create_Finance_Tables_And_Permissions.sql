-- ==============================================================================
-- Flyway Migration V14: Create Finance Domain Tables, RLS, and Seed Permissions
-- ==============================================================================

-- 1. Contas Financeiras (Bancárias, Caixas, Carteiras Digitais)
CREATE TABLE IF NOT EXISTS finance_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    bank_name VARCHAR(100),
    account_number VARCHAR(50),
    agency VARCHAR(30),
    initial_balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_finance_accounts_tenant_unit ON finance_accounts (tenant_id, unit_id);

-- 2. Plano de Contas / Categorias Financeiras
CREATE TABLE IF NOT EXISTS finance_categories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL, -- INCOME, EXPENSE
    dre_category_type VARCHAR(40) NOT NULL, -- GROSS_REVENUE, VARIABLE_COST, OPERATING_EXPENSE, TAXES_AND_DEDUCTIONS, FINANCIAL_RESULT, NON_OPERATING
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_finance_categories_tenant ON finance_categories (tenant_id);

-- 3. Contas a Receber (Receivables)
CREATE TABLE IF NOT EXISTS finance_receivables (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    customer_id UUID,
    customer_name VARCHAR(255),
    work_order_id UUID,
    order_number VARCHAR(50),
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    paid_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    due_date DATE NOT NULL,
    received_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL, -- PENDING, RECEIVED, PARTIALLY_RECEIVED, CANCELLED, REVERSED
    payment_method VARCHAR(50),
    account_id UUID REFERENCES finance_accounts(id),
    category_id UUID REFERENCES finance_categories(id),
    source_correlation_id VARCHAR(100) UNIQUE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_finance_receivables_tenant_unit ON finance_receivables (tenant_id, unit_id);
CREATE INDEX idx_finance_receivables_due_date ON finance_receivables (tenant_id, due_date);
CREATE INDEX idx_finance_receivables_status ON finance_receivables (tenant_id, status);

-- 4. Contas a Pagar (Payables)
CREATE TABLE IF NOT EXISTS finance_payables (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    inventory_purchase_id UUID,
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    paid_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    due_date DATE NOT NULL,
    paid_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL, -- PENDING, PAID, PARTIALLY_PAID, CANCELLED
    payment_method VARCHAR(50),
    account_id UUID REFERENCES finance_accounts(id),
    category_id UUID REFERENCES finance_categories(id),
    source_correlation_id VARCHAR(100) UNIQUE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_finance_payables_tenant_unit ON finance_payables (tenant_id, unit_id);
CREATE INDEX idx_finance_payables_due_date ON finance_payables (tenant_id, due_date);
CREATE INDEX idx_finance_payables_status ON finance_payables (tenant_id, status);

-- 5. Extrato de Transações Financeiras (Ledger)
CREATE TABLE IF NOT EXISTS finance_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES finance_accounts(id),
    category_id UUID REFERENCES finance_categories(id),
    receivable_id UUID REFERENCES finance_receivables(id),
    payable_id UUID REFERENCES finance_payables(id),
    type VARCHAR(20) NOT NULL, -- CREDIT, DEBIT
    amount NUMERIC(15, 2) NOT NULL,
    transaction_date DATE NOT NULL,
    competence_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    source_correlation_id VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_user_id UUID
);

CREATE INDEX idx_finance_transactions_tenant_account ON finance_transactions (tenant_id, account_id);
CREATE INDEX idx_finance_transactions_date ON finance_transactions (tenant_id, transaction_date);

-- 6. Despesas Recorrentes (Recurring Expenses)
CREATE TABLE IF NOT EXISTS finance_recurring_expenses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    category_id UUID REFERENCES finance_categories(id),
    description VARCHAR(255) NOT NULL,
    supplier_name VARCHAR(255),
    amount NUMERIC(15, 2) NOT NULL,
    frequency VARCHAR(20) NOT NULL, -- MONTHLY, WEEKLY, YEARLY
    due_day INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    next_generation_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_finance_recurring_tenant_unit ON finance_recurring_expenses (tenant_id, unit_id);

-- ==============================================================================
-- Row Level Security (RLS) Configuration
-- ==============================================================================
-- ==============================================================================
-- Row Level Security (RLS) Configuration
-- ==============================================================================
ALTER TABLE finance_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance_receivables ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance_payables ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance_recurring_expenses ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON finance_accounts
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

CREATE POLICY tenant_isolation_policy ON finance_categories
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

CREATE POLICY tenant_isolation_policy ON finance_receivables
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

CREATE POLICY tenant_isolation_policy ON finance_payables
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

CREATE POLICY tenant_isolation_policy ON finance_transactions
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

CREATE POLICY tenant_isolation_policy ON finance_recurring_expenses
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- ==============================================================================
-- Seed Finance Permissions
-- ==============================================================================
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'FINANCE_ACCOUNT_READ', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_ACCOUNT_WRITE', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_RECEIVABLE_READ', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_RECEIVABLE_WRITE', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_PAYABLE_READ', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_PAYABLE_WRITE', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_TRANSACTION_READ', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_TRANSACTION_WRITE', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_REPORT_READ', 'FINANCE')
ON CONFLICT (code) DO NOTHING;

-- Vincular novas permissões de finanças aos papéis existentes de Proprietário e Administrador
DO $$
DECLARE
    prop_role_id UUID;
    admin_role_id UUID;
    perm_rec RECORD;
BEGIN
    FOR prop_role_id IN SELECT id FROM roles WHERE name = 'Proprietário' LOOP
        FOR perm_rec IN SELECT id FROM permissions WHERE module = 'FINANCE' LOOP
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (prop_role_id, perm_rec.id)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;

    FOR admin_role_id IN SELECT id FROM roles WHERE name = 'ADMIN' LOOP
        FOR perm_rec IN SELECT id FROM permissions WHERE module = 'FINANCE' LOOP
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (admin_role_id, perm_rec.id)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

