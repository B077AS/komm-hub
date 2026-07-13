package com.kommhub.service;

import com.kommhub.model.db.Friend;
import com.kommhub.model.db.Friend.FriendStatus;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.summary.FriendSummary;
import com.kommhub.repository.FriendRepository;
import com.kommhub.repository.UserRepository;
import com.kommhub.websocket.managers.AppSessionsManager;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final AppSessionsManager sessionsManager;

    @Transactional(readOnly = true)
    public List<FriendSummary> getFriends(User user) {
        List<FriendSummary> friends = Stream.concat(
                friendRepository.findByRequesterAndStatus(user, FriendStatus.ACCEPTED).stream(),
                friendRepository.findByAddresseeAndStatus(user, FriendStatus.ACCEPTED).stream()
        ).map(this::toDto).toList();
        log.debug("User {} has {} accepted friend(s)", user.getUsername(), friends.size());
        return friends;
    }

    @Transactional(readOnly = true)
    public List<FriendSummary> getSentRequests(User user) {
        return friendRepository.findByRequesterAndStatus(user, FriendStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<FriendSummary> getReceivedRequests(User user) {
        return friendRepository.findByAddresseeAndStatus(user, FriendStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public FriendSummary sendRequest(User requester, String username) {
        log.debug("User {} sending friend request to '{}'", requester.getUsername(), username);
        User addressee = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));

        if (addressee.getUserId().equals(requester.getUserId()))
            throw new IllegalStateException("You cannot send a friend request to yourself");

        Optional<Friend> existing = friendRepository
                .findByRequesterAndAddressee(requester, addressee)
                .or(() -> friendRepository.findByRequesterAndAddressee(addressee, requester));

        if (existing.isPresent()) {
            FriendStatus s = existing.get().getStatus();
            if (s == FriendStatus.ACCEPTED) throw new IllegalStateException("Already friends");
            if (s == FriendStatus.PENDING)  throw new IllegalStateException("Request already pending");
            if (s == FriendStatus.BLOCKED)  throw new IllegalStateException("Cannot send request");
        }

        Friend friend = Friend.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendStatus.PENDING)
                .build();

        FriendSummary result = toDto(friendRepository.save(friend));
        log.debug("Friend request created with id {} from {} to {}", result.getFriendId(), requester.getUsername(), username);

        if (sessionsManager.isOnline(addressee.getUserId())) {
            log.debug("Notifying online user {} of incoming friend request", addressee.getUsername());
            FriendRequestPayload payload = FriendRequestPayload.builder()
                    .requesterId(requester.getUserId())
                    .requesterUsername(requester.getUsername())
                    .build();
            sessionsManager.sendToUser(
                    addressee.getUserId(),
                    new WsAppMessage(WsMessageType.FRIEND_REQUEST, payload)
            );
        }

        return result;
    }

    @Transactional
    public FriendSummary acceptRequest(User user, UUID friendId) {
        log.debug("User {} accepting friend request {}", user.getUsername(), friendId);
        Friend friend = getOrThrow(friendId);

        if (!friend.getAddressee().getUserId().equals(user.getUserId()))
            throw new IllegalStateException("You are not the addressee of this request");

        if (friend.getStatus() != FriendStatus.PENDING)
            throw new IllegalStateException("Request is not in PENDING state");

        friend.setStatus(FriendStatus.ACCEPTED);
        FriendSummary result = toDto(friendRepository.save(friend));

        User requester = friend.getRequester();
        if (sessionsManager.isOnline(requester.getUserId())) {
            log.debug("Notifying online user {} that their request was accepted", requester.getUsername());
            FriendRequestAcceptedPayload payload = FriendRequestAcceptedPayload.builder()
                    .addresseeId(user.getUserId())
                    .addresseeUsername(user.getUsername())
                    .build();
            sessionsManager.sendToUser(
                    requester.getUserId(),
                    new WsAppMessage(WsMessageType.FRIEND_REQUEST_ACCEPT, payload)
            );
        }

        return result;
    }

    @Transactional
    public void declineRequest(User user, UUID friendId) {
        log.debug("User {} declining friend request {}", user.getUsername(), friendId);
        Friend friend = getOrThrow(friendId);

        if (!friend.getAddressee().getUserId().equals(user.getUserId()))
            throw new IllegalStateException("You are not the addressee of this request");

        if (friend.getStatus() != FriendStatus.PENDING)
            throw new IllegalStateException("Request is not in PENDING state");

        friendRepository.delete(friend);

        User requester = friend.getRequester();
        if (sessionsManager.isOnline(requester.getUserId())) {
            log.debug("Notifying online user {} that their request was declined", requester.getUsername());
            sessionsManager.sendToUser(
                    requester.getUserId(),
                    new WsAppMessage(WsMessageType.FRIEND_REQUEST_DECLINED, new FriendRequestDeclinedPayload(friendId))
            );
        }
    }

    @Transactional
    public void removeFriend(User user, UUID friendId) {
        log.debug("User {} removing friend record {}", user.getUsername(), friendId);
        Friend friend = getOrThrow(friendId);

        boolean isParty = friend.getRequester().getUserId().equals(user.getUserId())
                || friend.getAddressee().getUserId().equals(user.getUserId());

        if (!isParty) throw new IllegalStateException("You are not part of this friendship");
        if (friend.getStatus() != FriendStatus.ACCEPTED)
            throw new IllegalStateException("Cannot remove a non-accepted friend");

        friendRepository.delete(friend);

        // Notify the other party
        UUID otherId = friend.getRequester().getUserId().equals(user.getUserId())
                ? friend.getAddressee().getUserId()
                : friend.getRequester().getUserId();

        if (sessionsManager.isOnline(otherId)) {
            log.debug("Notifying online user {} that they were removed as a friend", otherId);
            sessionsManager.sendToUser(
                    otherId,
                    new WsAppMessage(WsMessageType.FRIEND_REMOVED, new FriendRemovedPayload(friendId))
            );
        }
    }

    @Transactional
    public void cancelRequest(User user, UUID friendId) {
        log.debug("User {} cancelling friend request {}", user.getUsername(), friendId);
        Friend friend = getOrThrow(friendId);

        if (!friend.getRequester().getUserId().equals(user.getUserId()))
            throw new IllegalStateException("You are not the requester of this request");

        if (friend.getStatus() != FriendStatus.PENDING)
            throw new IllegalStateException("Request is not in PENDING state");

        friendRepository.delete(friend);

        User addressee = friend.getAddressee();
        if (sessionsManager.isOnline(addressee.getUserId())) {
            sessionsManager.sendToUser(
                    addressee.getUserId(),
                    new WsAppMessage(WsMessageType.FRIEND_REQUEST_CANCELLED, new FriendRequestCancelledPayload(friendId))
            );
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Friend getOrThrow(UUID friendId) {
        return friendRepository.findById(friendId)
                .orElseThrow(() -> new EntityNotFoundException("Friend record not found: " + friendId));
    }

    public FriendSummary toDto(Friend f) {
        return FriendSummary.builder()
                .friendId(f.getFriendId())
                .requester(f.getRequester().getUserId())
                .addressee(f.getAddressee().getUserId())
                .status(f.getStatus())
                .build();
    }
}