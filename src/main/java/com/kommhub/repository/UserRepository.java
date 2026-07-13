package com.kommhub.repository;

import com.kommhub.model.db.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query(value = "SELECT * FROM users WHERE username = ?1 OR email = ?1 LIMIT 1", nativeQuery = true)
    Optional<User> findByUsernameOrEmail(String username);

    Optional<User> findByUserId(UUID userId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findAllByEmailVerifiedFalseAndCreatedAtBefore(java.time.LocalDateTime cutoff);

    long countByRole(User.Role role);
}
