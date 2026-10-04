package com.kommhub.repository;

import com.kommhub.model.db.InstallationUptimeDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallationUptimeDailyRepository extends JpaRepository<InstallationUptimeDaily, Long> {

    Optional<InstallationUptimeDaily> findByInstallationIdAndDay(UUID installationId, LocalDate day);

    List<InstallationUptimeDaily> findByInstallationIdAndDayGreaterThanEqualOrderByDayAsc(
            UUID installationId, LocalDate from);

    @Query("select max(d.day) from InstallationUptimeDaily d where d.installationId = :installationId")
    Optional<LocalDate> findMaxDay(@Param("installationId") UUID installationId);

    @Modifying
    @Query("delete from InstallationUptimeDaily d where d.installationId = :installationId")
    void deleteByInstallationId(@Param("installationId") UUID installationId);
}
