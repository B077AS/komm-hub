package com.kommhub.repository;

import com.kommhub.model.db.Installation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallationRepository extends JpaRepository<Installation, UUID> {

    List<Installation> findByOwnerId(UUID ownerId);

    Optional<Installation> findBySetupToken(String setupToken);
}
