package com.kommhub.repository;

import com.kommhub.model.db.DirectMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    @Query("""
    SELECT m FROM DirectMessage m
    WHERE ((m.senderId = :userId AND m.recipientId = :partnerId)
        OR (m.senderId = :partnerId AND m.recipientId = :userId))
      AND m.sentAt < :cursor
      AND m.sentAt > :hiddenBefore
    ORDER BY m.sentAt DESC
    """)
    List<DirectMessage> findConversationBeforeCursor(
            @Param("userId") UUID userId,
            @Param("partnerId") UUID partnerId,
            @Param("cursor") LocalDateTime cursor,
            @Param("hiddenBefore") LocalDateTime hiddenBefore,
            Pageable pageable);

    @Query("""
    SELECT m FROM DirectMessage m
    LEFT JOIN FETCH m.reactions
    WHERE m.messageId IN :ids
    """)
    List<DirectMessage> findByIdsWithReactions(@Param("ids") List<UUID> ids);

    @Query(value = """
    SELECT * FROM (
        SELECT dm.*, ROW_NUMBER() OVER (
            PARTITION BY LEAST(CAST(dm.sender_id AS VARCHAR), CAST(dm.recipient_id AS VARCHAR)),
                         GREATEST(CAST(dm.sender_id AS VARCHAR), CAST(dm.recipient_id AS VARCHAR))
            ORDER BY dm.sent_at DESC
        ) AS rn
        FROM direct_messages dm
        LEFT JOIN dm_conversation_hidden h
            ON h.user_id = :userId
            AND (
                (h.other_user_id = dm.sender_id AND dm.recipient_id = :userId)
                OR (h.other_user_id = dm.recipient_id AND dm.sender_id = :userId)
            )
        WHERE (dm.sender_id = :userId OR dm.recipient_id = :userId)
          AND (h.hidden_before IS NULL OR dm.sent_at > h.hidden_before)
    ) ranked WHERE rn = 1
    ORDER BY sent_at DESC
    """, nativeQuery = true)
    List<DirectMessage> findLatestMessagePerConversation(@Param("userId") UUID userId);

    @Query("""
    SELECT m FROM DirectMessage m
    WHERE (m.senderId = :userId AND m.recipientId = :partnerId)
       OR (m.senderId = :partnerId AND m.recipientId = :userId)
    """)
    List<DirectMessage> findAllBetweenUsers(@Param("userId") UUID userId, @Param("partnerId") UUID partnerId);
}
