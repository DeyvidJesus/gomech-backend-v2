-- ============================================================================
-- V2: Add optimistic locking version columns to mutable domain tables
-- ============================================================================

-- 1. IAM & Multi-Tenant Core
ALTER TABLE tenants ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE units ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE roles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2. CRM
ALTER TABLE customers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vehicles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 3. Inventory
ALTER TABLE suppliers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 4. Operations
ALTER TABLE quotes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE work_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 5. Finance & Billing
ALTER TABLE subscriptions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE financial_transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
