package com.kommhub.repository;

import com.kommhub.model.db.Friend;
import com.kommhub.model.db.Friend.FriendStatus;
import com.kommhub.model.db.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendRepository extends JpaRepository<Friend, UUID> {

    List<Friend> findByRequesterAndStatus(User requester, FriendStatus status);

    List<Friend> findByAddresseeAndStatus(User addressee, FriendStatus status);

    Optional<Friend> findByRequesterAndAddressee(User requester, User addressee);

    @Query("""
    SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friend f
    WHERE ((f.requester.userId = :userId1 AND f.addressee.userId = :userId2)
        OR (f.requester.userId = :userId2 AND f.addressee.userId = :userId1))
      AND f.status = 'ACCEPTED'
    """)
    boolean areFriends(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);
}