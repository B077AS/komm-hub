package com.kommhub.repository;

import com.kommhub.model.db.DmConversationHidden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DmConversationHiddenRepository extends JpaRepository<DmConversationHidden, DmConversationHidden.PK> {

    Optional<DmConversationHidden> findByUserIdAndOtherUserId(UUID userId, UUID otherUserId);

    @Transactional
    void deleteByUserIdAndOtherUserId(UUID userId, UUID otherUserId);
}
