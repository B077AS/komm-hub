package com.kommhub.repository;

import com.kommhub.model.db.BetaKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BetaKeyRepository extends JpaRepository<BetaKey, UUID> {

    Optional<BetaKey> findByKeyValue(String keyValue);

    List<BetaKey> findAllByOrderByCreatedAtDesc();

    void deleteByUsedByUserId(UUID usedByUserId);
}
