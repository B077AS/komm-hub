package com.kommhub.repository;

import com.kommhub.model.db.BadgeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BadgeTokenRepository extends JpaRepository<BadgeToken, UUID> {

    Optional<BadgeToken> findByTokenValue(String tokenValue);

    Optional<BadgeToken> findFirstByBadgeIdOrderByCreatedAtDesc(UUID badgeId);

    void deleteByBadgeId(UUID badgeId);
}
