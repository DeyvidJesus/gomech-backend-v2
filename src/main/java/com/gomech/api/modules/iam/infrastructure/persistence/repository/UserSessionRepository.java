package com.gomech.api.modules.iam.infrastructure.persistence.repository;

import com.gomech.api.modules.iam.infrastructure.persistence.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByRefreshToken(String refreshToken);

    List<UserSession> findAllByUserIdAndRevokedFalseOrderByCreatedAtDesc(UUID userId);

    List<UserSession> findAllByFamilyId(UUID familyId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revoked = true, s.revokedAt = :now WHERE s.familyId = :familyId AND s.revoked = false")
    int revokeAllByFamilyId(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revoked = true, s.revokedAt = :now WHERE s.user.id = :userId AND s.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
