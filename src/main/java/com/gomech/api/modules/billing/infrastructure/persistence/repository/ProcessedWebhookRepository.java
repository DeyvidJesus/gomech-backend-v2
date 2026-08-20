package com.gomech.api.modules.billing.infrastructure.persistence.repository;

import com.gomech.api.modules.billing.infrastructure.persistence.model.ProcessedWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedWebhookRepository extends JpaRepository<ProcessedWebhook, UUID> {

    Optional<ProcessedWebhook> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
