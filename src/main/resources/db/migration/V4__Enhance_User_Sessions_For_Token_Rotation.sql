-- ============================================================================
-- V4: Enhance user_sessions table for JWT Refresh Token Rotation & Revocation
-- ============================================================================

ALTER TABLE user_sessions
    ADD COLUMN tenant_id UUID REFERENCES tenants(id),
    ADD COLUMN family_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    ADD COLUMN replaced_by UUID REFERENCES user_sessions(id),
    ADD COLUMN is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN revoked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN user_agent TEXT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Indexes for high performance session queries and revocation
CREATE INDEX idx_user_sessions_family_id ON user_sessions(family_id);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_user_active ON user_sessions(user_id, is_revoked);
CREATE INDEX idx_user_sessions_tenant_id ON user_sessions(tenant_id);
