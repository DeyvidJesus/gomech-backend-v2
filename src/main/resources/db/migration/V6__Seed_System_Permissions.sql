-- ==============================================================================
-- V6: Seed Standard System Permissions for RBAC / PBAC
-- ==============================================================================

INSERT INTO permissions (id, code, module) VALUES
    -- IAM (Identity & Access Management)
    (uuid_generate_v4(), 'IAM_USER_READ', 'IAM'),
    (uuid_generate_v4(), 'IAM_USER_WRITE', 'IAM'),
    (uuid_generate_v4(), 'IAM_USER_DELETE', 'IAM'),
    (uuid_generate_v4(), 'IAM_ROLE_READ', 'IAM'),
    (uuid_generate_v4(), 'IAM_ROLE_WRITE', 'IAM'),
    (uuid_generate_v4(), 'IAM_UNIT_READ', 'IAM'),
    (uuid_generate_v4(), 'IAM_UNIT_WRITE', 'IAM'),

    -- CRM (Customer Relationship Management)
    (uuid_generate_v4(), 'CRM_CUSTOMER_READ', 'CRM'),
    (uuid_generate_v4(), 'CRM_CUSTOMER_WRITE', 'CRM'),
    (uuid_generate_v4(), 'CRM_CUSTOMER_DELETE', 'CRM'),
    (uuid_generate_v4(), 'CRM_VEHICLE_READ', 'CRM'),
    (uuid_generate_v4(), 'CRM_VEHICLE_WRITE', 'CRM'),
    (uuid_generate_v4(), 'CRM_VEHICLE_DELETE', 'CRM'),

    -- OPERATIONS (Work Orders & Quotes)
    (uuid_generate_v4(), 'OPERATIONS_ORDER_READ', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_ORDER_WRITE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_ORDER_EXECUTE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_ORDER_CLOSE', 'OPERATIONS'),

    -- INVENTORY (Stock & Parts)
    (uuid_generate_v4(), 'INVENTORY_PRODUCT_READ', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_PRODUCT_WRITE', 'INVENTORY'),
    (uuid_generate_v4(), 'INVENTORY_MOVEMENT_WRITE', 'INVENTORY'),

    -- FINANCE (Cashflow & Payments)
    (uuid_generate_v4(), 'FINANCE_TRANSACTION_READ', 'FINANCE'),
    (uuid_generate_v4(), 'FINANCE_TRANSACTION_WRITE', 'FINANCE')
ON CONFLICT (code) DO NOTHING;
