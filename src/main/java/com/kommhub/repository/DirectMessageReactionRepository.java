package com.kommhub.repository;

import com.kommhub.model.db.DirectMessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectMessageReactionRepository extends JpaRepository<DirectMessageReaction, DirectMessageReaction.DirectMessageReactionId> {
}
