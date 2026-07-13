package com.kommhub.repository;

import com.kommhub.model.db.InstallationAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallationAccessTokenRepository extends JpaRepository<InstallationAccessToken, UUID> {

    Optional<InstallationAccessToken> findByCode(String code);

    List<InstallationAccessToken> findByInstallationIdAndUsedFalse(UUID installationId);
}
