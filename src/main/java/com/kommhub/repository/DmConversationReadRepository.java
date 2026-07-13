package com.kommhub.repository;

import com.kommhub.model.db.DmConversationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DmConversationReadRepository extends JpaRepository<DmConversationRead, DmConversationRead.PK> {

    Optional<DmConversationRead> findByUserIdAndPartnerId(UUID userId, UUID partnerId);

    List<DmConversationRead> findAllByUserId(UUID userId);

    @Transactional
    void deleteByUserIdAndPartnerId(UUID userId, UUID partnerId);
}
