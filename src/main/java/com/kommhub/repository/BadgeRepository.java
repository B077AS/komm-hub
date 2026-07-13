package com.kommhub.repository;

import com.kommhub.model.db.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    Optional<Badge> findByCode(String code);

    boolean existsByCode(String code);

    List<Badge> findAllByOrderByPositionAscCreatedAtAsc();
}
