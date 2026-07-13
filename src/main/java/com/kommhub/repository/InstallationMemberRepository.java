package com.kommhub.repository;

import com.kommhub.model.db.InstallationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallationMemberRepository extends JpaRepository<InstallationMember, UUID> {

    List<InstallationMember> findByUserId(UUID userId);

    List<InstallationMember> findByInstallationId(UUID installationId);

    Optional<InstallationMember> findByInstallationIdAndUserId(UUID installationId, UUID userId);

    boolean existsByInstallationIdAndUserId(UUID installationId, UUID userId);
}
