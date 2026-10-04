package com.kommhub.repository;

import com.kommhub.model.db.InstallationStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallationStatusEventRepository extends JpaRepository<InstallationStatusEvent, UUID> {

    Optional<InstallationStatusEvent> findFirstByInstallationIdOrderByOccurredAtDesc(UUID installationId);

    List<InstallationStatusEvent> findByInstallationIdOrderByOccurredAtDesc(UUID installationId);

    List<InstallationStatusEvent> findTop20ByInstallationIdOrderByOccurredAtDesc(UUID installationId);

    @Query("select e from InstallationStatusEvent e where e.installationId = :installationId " +
            "and e.occurredAt < :before order by e.occurredAt desc limit 1")
    Optional<InstallationStatusEvent> findLastEventBefore(@Param("installationId") UUID installationId,
                                                            @Param("before") LocalDateTime before);

    @Query("select e from InstallationStatusEvent e where e.installationId = :installationId " +
            "and e.occurredAt >= :from and e.occurredAt < :to order by e.occurredAt asc")
    List<InstallationStatusEvent> findInRange(@Param("installationId") UUID installationId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    @Query("select e from InstallationStatusEvent e where e.installationId = :installationId " +
            "and e.occurredAt >= :since order by e.occurredAt desc")
    List<InstallationStatusEvent> findSince(@Param("installationId") UUID installationId,
                                             @Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from InstallationStatusEvent e where e.installationId = :installationId")
    void deleteByInstallationId(@Param("installationId") UUID installationId);

    // Earliest known event per installation - the rollup job never fabricates "uptime" for days
    // before an installation existed in this table.
    @Query("select min(e.occurredAt) from InstallationStatusEvent e where e.installationId = :installationId")
    Optional<LocalDateTime> findEarliestOccurredAt(@Param("installationId") UUID installationId);
}
