package com.kommhub.repository;

import com.kommhub.model.db.InviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InviteLinkRepository extends JpaRepository<InviteLink, UUID> {
    Optional<InviteLink> findByCode(String code);

    List<InviteLink> findByServerIdAndActiveTrue(UUID serverId);

    @Modifying
    @Query("delete from InviteLink i where i.expiresAt is not null and i.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from InviteLink i where i.serverId = :serverId")
    void deleteByServerId(@Param("serverId") UUID serverId);
}
