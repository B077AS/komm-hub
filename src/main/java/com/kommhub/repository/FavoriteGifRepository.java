package com.kommhub.repository;

import com.kommhub.model.db.FavoriteGif;
import com.kommhub.model.db.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface FavoriteGifRepository extends JpaRepository<FavoriteGif, UUID> {
    List<FavoriteGif> findByUserOrderByCreatedAtDesc(User user);
    boolean existsByUserAndGifId(User user, String gifId);
    @Transactional
    void deleteByUserAndGifId(User user, String gifId);
}
