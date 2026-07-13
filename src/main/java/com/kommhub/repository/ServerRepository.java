package com.kommhub.repository;

import com.kommhub.model.db.Server;
import com.kommhub.websocket.messages.payloads.ServerMemberPayload;
import com.kommhub.websocket.messages.payloads.ServerPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServerRepository extends JpaRepository<Server, UUID> {

    // Updated to order by display_order, with nulls last, then by joined date
    @Query("SELECT s, sm, u, COUNT(DISTINCT sm2.userId), i " +
            "FROM Server s " +
            "JOIN ServerMember sm ON s.serverId = sm.serverId " +
            "JOIN User u ON s.ownerId = u.userId " +
            "LEFT JOIN ServerMember sm2 ON s.serverId = sm2.serverId " +
            "LEFT JOIN Installation i ON s.installationId = i.installationId " +
            "WHERE sm.userId = :userId AND (s.pendingDeletion IS NULL OR s.pendingDeletion = false) " +
            "GROUP BY s.serverId, sm.userId, sm.serverId, sm.displayOrder, sm.joinedAt, u.userId, i.installationId " +
            "ORDER BY COALESCE(sm.displayOrder, 999999), sm.joinedAt ASC")
    List<Object[]> findServersWithMembershipByUserId(@Param("userId") UUID userId);

    long countByInstallationId(UUID installationId);

    List<Server> findByInstallationId(UUID installationId);

    // Excludes servers pending deletion so a reconnecting installation does not re-create them.
    @Query("SELECT new com.kommhub.websocket.messages.payloads.ServerPayload(s.serverId, s.serverName, s.description, s.ownerId, s.createdAt) " +
            "FROM Server s WHERE s.installationId = :installationId AND (s.pendingDeletion IS NULL OR s.pendingDeletion = false)")
    List<ServerPayload> findServersByInstallationId(@Param("installationId") UUID installationId);

    @Query("SELECT new com.kommhub.websocket.messages.payloads.ServerMemberPayload(sm.serverId, sm.userId, sm.joinedAt, sm.role) " +
            "FROM ServerMember sm " +
            "JOIN Server s ON sm.serverId = s.serverId " +
            "WHERE s.installationId = :installationId AND (s.pendingDeletion IS NULL OR s.pendingDeletion = false)")
    List<ServerMemberPayload> findMembersByInstallationId(@Param("installationId") UUID installationId);

    @Query("SELECT s.serverId FROM Server s WHERE s.installationId = :installationId AND s.pendingDeletion = true")
    List<UUID> findPendingDeletionServerIdsByInstallationId(@Param("installationId") UUID installationId);
}