package com.kommhub.repository;

import com.kommhub.model.db.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    @Query("select ub from UserBadge ub join fetch ub.badge where ub.userId = :userId")
    List<UserBadge> findAllWithBadgeByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndBadgeId(UUID userId, UUID badgeId);

    long countByBadgeId(UUID badgeId);

    void deleteByUserId(UUID userId);

    void deleteByBadgeId(UUID badgeId);
}
