package com.gomech.api.core.tenancy;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Manages PostgreSQL session settings for Row-Level Security (RLS) defense in depth.
 *
 * <p>Sets {@code app.current_tenant} and {@code app.current_unit} via {@code SET LOCAL},
 * ensuring variables are bound strictly to the active transaction and automatically cleared
 * on transaction completion.
 */
@Component
public class PostgresRlsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(PostgresRlsSessionManager.class);

    /**
     * Applies tenant and unit session variables to an active JDBC connection.
     */
    public static void applySessionContext(Connection connection, UUID tenantId, UUID unitId) throws SQLException {
        if (connection == null) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            if (tenantId != null) {
                stmt.execute("SET LOCAL app.current_tenant = '" + tenantId + "';");
            } else {
                stmt.execute("SET LOCAL app.current_tenant = '';");
            }

            if (unitId != null) {
                stmt.execute("SET LOCAL app.current_unit = '" + unitId + "';");
            } else {
                stmt.execute("SET LOCAL app.current_unit = '';");
            }
        }
    }

    /**
     * Applies tenant and unit session variables to an active JPA EntityManager inside a transaction.
     */
    public static void applySessionContext(EntityManager entityManager, UUID tenantId, UUID unitId) {
        if (entityManager == null) {
            return;
        }

        try {
            if (tenantId != null) {
                entityManager.createNativeQuery("SET LOCAL app.current_tenant = '" + tenantId + "'").executeUpdate();
            } else {
                entityManager.createNativeQuery("SET LOCAL app.current_tenant = ''").executeUpdate();
            }

            if (unitId != null) {
                entityManager.createNativeQuery("SET LOCAL app.current_unit = '" + unitId + "'").executeUpdate();
            } else {
                entityManager.createNativeQuery("SET LOCAL app.current_unit = ''").executeUpdate();
            }
        } catch (Exception ex) {
            log.debug("Could not set RLS session variables on EntityManager (may not be in active transaction): {}", ex.getMessage());
        }
    }
}
