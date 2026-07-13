package com.kommhub.repository;

import com.kommhub.model.db.ServerMember;
import com.kommhub.model.db.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServerMemberRepository extends JpaRepository<ServerMember, ServerMember.ServerMemberId> {

    // Find a specific server member by composite key
    @Query("SELECT sm FROM ServerMember sm WHERE sm.userId = :userId AND sm.serverId = :serverId")
    Optional<ServerMember> findByUserIdAndServerId(@Param("userId") UUID userId, @Param("serverId") UUID serverId);

    Long countByServerId(UUID serverId);

    boolean existsByServerIdAndUserId(UUID serverId, UUID userId);

    List<ServerMember> findByServerId(UUID serverId);

    List<ServerMember> findByUserId(UUID userId);

    @Query("SELECT sm.userId FROM ServerMember sm WHERE sm.serverId = :serverId")
    List<UUID> findUserIdsByServerId(@Param("serverId") UUID serverId);

    @Modifying
    @Query("DELETE FROM ServerMember sm WHERE sm.serverId = :serverId")
    void deleteByServerId(@Param("serverId") UUID serverId);

    @Query("SELECT MAX(sm.displayOrder) FROM ServerMember sm WHERE sm.userId = :userId")
    java.util.Optional<Integer> findMaxDisplayOrderByUserId(@Param("userId") UUID userId);

    // Paginated members excluding the requester
    @Query("SELECT sm FROM ServerMember sm WHERE sm.serverId = :serverId AND sm.userId != :excludeId")
    Page<ServerMember> findByServerIdExcluding(
            @Param("serverId") UUID serverId,
            @Param("excludeId") UUID excludeId,
            Pageable pageable);

    // All members whose user status is in the given set (online/away/dnd)
    @Query("SELECT sm FROM ServerMember sm JOIN FETCH sm.user u WHERE sm.serverId = :serverId AND u.status IN :statuses")
    List<ServerMember> findOnlineByServerId(
            @Param("serverId") UUID serverId,
            @Param("statuses") List<User.UserStatus> statuses);

    // Paginated members whose user status is NOT in the given set (offline/invisible)
    @Query(value = "SELECT sm FROM ServerMember sm JOIN FETCH sm.user u WHERE sm.serverId = :serverId AND u.status NOT IN :statuses",
           countQuery = "SELECT COUNT(sm) FROM ServerMember sm JOIN sm.user u WHERE sm.serverId = :serverId AND u.status NOT IN :statuses")
    Page<ServerMember> findOfflineByServerId(
            @Param("serverId") UUID serverId,
            @Param("statuses") List<User.UserStatus> statuses,
            Pageable pageable);

    // Case-insensitive username search across all server members
    @Query("SELECT sm FROM ServerMember sm JOIN FETCH sm.user u WHERE sm.serverId = :serverId AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ServerMember> searchByUsername(
            @Param("serverId") UUID serverId,
            @Param("query") String query,
            Pageable pageable);
}