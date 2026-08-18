package com.gomech.api.database;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Tenant;
import com.gomech.api.modules.iam.infrastructure.persistence.model.User;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.TenantRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests validating persistence and transaction conventions:
 * 1. Transactional rollback integrity on unhandled exceptions
 * 2. Optimistic locking (@Version) collision detection
 * 3. Version increment on successful updates
 * 4. Partial index behavior for soft-deleted records
 */
@SpringBootTest
@Testcontainers
@Import(PersistenceTransactionsAndConcurrencyIT.TransactionalTestHelper.class)
class PersistenceTransactionsAndConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionalTestHelper testHelper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Oficina Teste Concorrencia");
        tenant.setCnpj("11.222.333/0001-" + UUID.randomUUID().toString().substring(0, 2));
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Transaction rolls back completely when an application service throws an exception")
    void transactionRollsBackOnException() {
        String testEmail = "rollback-test-" + UUID.randomUUID() + "@gomech.com";

        assertThrows(IllegalStateException.class, () ->
                testHelper.createUserAndFail(tenantId, "Rollback User", testEmail));

        // Verify that the user was NOT saved to the database due to rollback
        Optional<User> user = userRepository.findByEmail(testEmail);
        assertTrue(user.isEmpty(), "User should not exist in database after transaction rollback");
    }

    @Test
    @DisplayName("Optimistic locking increments version number on entity update")
    void optimisticLockingIncrementsVersionOnUpdate() {
        User user = new User();
        user.setTenantId(tenantId);
        user.setName("Versioned User");
        user.setEmail("version-" + UUID.randomUUID() + "@gomech.com");
        user.setPasswordHash("hashed_pwd");
        user = userRepository.saveAndFlush(user);

        assertEquals(0L, user.getVersion(), "Initial version should be 0");

        user.setName("Versioned User Updated");
        User updated = userRepository.saveAndFlush(user);

        assertEquals(1L, updated.getVersion(), "Version should increment to 1 after update");
    }

    @Test
    @DisplayName("Optimistic locking detects concurrent update collisions and throws exception")
    void optimisticLockingPreventsLostUpdates() {
        Tenant tenant = new Tenant();
        tenant.setName("Initial Name");
        tenant.setCnpj("99.888.777/0001-" + UUID.randomUUID().toString().substring(0, 2));
        Tenant savedTenant = tenantRepository.saveAndFlush(tenant);
        UUID targetId = savedTenant.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // First transaction updates the tenant, incrementing version to 1
        txTemplate.executeWithoutResult(status -> {
            Tenant t = tenantRepository.findById(targetId).orElseThrow();
            t.setName("Updated by TX 1");
            tenantRepository.saveAndFlush(t);
        });

        // Second transaction tries to update using the stale version (0)
        assertThrows(OptimisticLockingFailureException.class, () -> {
            txTemplate.executeWithoutResult(status -> {
                // Manually set stale version onto an entity to simulate stale concurrent read
                savedTenant.setName("Updated by TX 2 with Stale Version");
                tenantRepository.saveAndFlush(savedTenant);
            });
        });
    }

    @Test
    @DisplayName("Soft delete partial unique index allows re-creating record with same email")
    void softDeleteAllowsRecreatingSameEmail() {
        String sharedEmail = "reused-" + UUID.randomUUID() + "@gomech.com";

        // Create first user
        User firstUser = new User();
        firstUser.setTenantId(tenantId);
        firstUser.setName("First Active User");
        firstUser.setEmail(sharedEmail);
        firstUser.setPasswordHash("hashed");
        userRepository.saveAndFlush(firstUser);

        // Soft delete the first user
        firstUser.setDeletedAt(OffsetDateTime.now());
        userRepository.saveAndFlush(firstUser);

        // Create second user with same email in same tenant - must succeed due to partial index
        User secondUser = new User();
        secondUser.setTenantId(tenantId);
        secondUser.setName("Second Active User");
        secondUser.setEmail(sharedEmail);
        secondUser.setPasswordHash("hashed");

        assertDoesNotThrow(() -> userRepository.saveAndFlush(secondUser),
                "Should allow inserting active user with same email as a soft-deleted user");
    }

    @TestConfiguration
    static class TransactionalTestHelper {

        @Autowired
        private UserRepository userRepository;

        @Transactional
        public void createUserAndFail(UUID tenantId, String name, String email) {
            User user = new User();
            user.setTenantId(tenantId);
            user.setName(name);
            user.setEmail(email);
            user.setPasswordHash("password_hash");
            userRepository.save(user);

            // Trigger failure to test rollback
            throw new IllegalStateException("Simulated failure inside transaction");
        }
    }
}
