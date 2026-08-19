-- ============================================================================
-- V5: Create User Identities Table for Federated OAuth/OIDC Providers
-- ============================================================================

CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_user_identities_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_identities_user_id ON user_identities(user_id);
CREATE INDEX idx_user_identities_tenant_id ON user_identities(tenant_id);
CREATE INDEX idx_user_identities_provider_subject ON user_identities(provider, provider_subject);
CREATE INDEX idx_user_identities_email ON user_identities(email);

-- Row-Level Security (RLS) for user_identities
ALTER TABLE user_identities ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON user_identities
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);
