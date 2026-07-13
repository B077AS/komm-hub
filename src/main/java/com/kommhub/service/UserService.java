package com.kommhub.service;

import com.kommhub.model.db.Friend;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.request.UserUpdateRequest;
import com.kommhub.model.dto.response.UserBatchCacheResponse;
import com.kommhub.model.dto.response.UserCacheResponse;
import com.kommhub.model.dto.response.UserLookupResponse;
import com.kommhub.model.dto.response.UserStatusDto;
import com.kommhub.model.dto.summary.ChannelUserSummary;
import com.kommhub.model.dto.summary.FriendSummary;
import com.kommhub.model.dto.summary.MainUserSummary;
import com.kommhub.model.dto.summary.UserSummary;
import com.kommhub.repository.FriendRepository;
import com.kommhub.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final BadgeService badgeService;

    @Value("${app.avatars.dir:data/UserAvatars}")
    private String avatarsDir;

    public MainUserSummary updateUser(User user, UserUpdateRequest dto) {

        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            user.setUsername(dto.getUsername().trim());
        }
        // Email is intentionally never applied here — users cannot change their own email.
        if (dto.getStatusMessage() != null) {
            user.setStatusMessage(dto.getStatusMessage().trim());
        }
        if (dto.getStatusEmoji() != null) {
            user.setStatusEmoji(dto.getStatusEmoji().isBlank() ? null : dto.getStatusEmoji().trim());
        }
        if (dto.getStatus() != null) {
            user.setPreferredStatus(dto.getStatus());
            user.setStatus(dto.getStatus());
        }
        if (dto.getDmPrivacy() != null) {
            user.setDmPrivacy(dto.getDmPrivacy());
        }

        String previousAvatarFilename = null;
        if (dto.getAvatar() != null && dto.getAvatarImageFormat() != null) {
            try {
                previousAvatarFilename = user.getAvatarFilename();

                byte[] avatarBytes = Base64.getDecoder().decode(dto.getAvatar());
                String filename = user.getUserId() + "." + dto.getAvatarImageFormat();
                Path dest = Paths.get(avatarsDir, filename);
                Files.createDirectories(dest.getParent());
                Files.write(dest, avatarBytes);
                user.setAvatarFilename(filename);

                if (filename.equals(previousAvatarFilename)) {
                    previousAvatarFilename = null;
                }
            } catch (Exception e) {
                log.error("Failed to save avatar for user {}: {}", user.getUserId(), e.getMessage());
                throw new RuntimeException("Failed to save avatar: " + e.getMessage(), e);
            }
        }

        user = userRepository.save(user);

        if (previousAvatarFilename != null) {
            try {
                Files.deleteIfExists(Paths.get(avatarsDir, previousAvatarFilename));
            } catch (Exception e) {
                log.warn("Failed to delete previous avatar {}: {}", previousAvatarFilename, e.getMessage());
            }
        }

        return toDto(user);
    }

    public MainUserSummary updateStatus(User user, User.UserStatus status) {
        user.setPreferredStatus(status);
        user.setStatus(status);
        user = userRepository.save(user);
        return toDto(user);
    }

    public void updateAvState(User user, boolean micEnabled, boolean speakerEnabled) {
        if (user.isMicEnabled() == micEnabled && user.isSpeakerEnabled() == speakerEnabled) {
            return;
        }
        user.setMicEnabled(micEnabled);
        user.setSpeakerEnabled(speakerEnabled);
        userRepository.save(user);
    }

    public UserCacheResponse getAvatar(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        byte[] avatarBytes = readAvatarBytes(user);
        String avatarBase64 = avatarBytes != null ? Base64.getEncoder().encodeToString(avatarBytes) : null;

        return UserCacheResponse.builder()
                .username(user.getUsername())
                .avatar(avatarBase64)
                .avatarImageFormat(resolveAvatarFormat(user, avatarBytes))
                .build();
    }

    public MainUserSummary toDto(User user) {
        byte[] avatarBytes = readAvatarBytes(user);
        String avatarBase64 = avatarBytes != null ? Base64.getEncoder().encodeToString(avatarBytes) : null;

        return MainUserSummary.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .statusMessage(user.getStatusMessage())
                .statusEmoji(user.getStatusEmoji())
                .avatar(avatarBase64)
                .avatarImageFormat(resolveAvatarFormat(user, avatarBytes))
                .micEnabled(user.isMicEnabled())
                .speakerEnabled(user.isSpeakerEnabled())
                .dmPrivacy(user.getDmPrivacy())
                .role(user.getRole())
                .badges(badgeService.getBadgesForUser(user))
                .build();
    }

    public UserSummary toUserSummary(User target, User requester) {
        Friend.FriendStatus[] activeStatuses = {
                Friend.FriendStatus.PENDING,
                Friend.FriendStatus.ACCEPTED,
                Friend.FriendStatus.BLOCKED
        };

        // Look up any active relationship between the two users (either direction)
        var rel = friendRepository
                .findByRequesterAndAddressee(requester, target)
                .or(() -> friendRepository.findByRequesterAndAddressee(target, requester));

        FriendSummary friendSummary = null;
        LocalDateTime friendsSince = null;

        if (rel.isPresent()) {
            Friend f = rel.get();
            boolean active = Arrays.stream(activeStatuses)
                    .anyMatch(s -> s == f.getStatus());
            if (active) {
                friendSummary = FriendSummary.builder()
                        .friendId(f.getFriendId())
                        .requester(f.getRequester().getUserId())
                        .addressee(f.getAddressee().getUserId())
                        .status(f.getStatus())
                        .build();
                if (f.getStatus() == Friend.FriendStatus.ACCEPTED) {
                    friendsSince = f.getUpdatedAt();
                }
            }
        }

        return UserSummary.builder()
                .userId(target.getUserId())
                .username(target.getUsername())
                .status(target.getStatus())
                .statusMessage(target.getStatusMessage())
                .statusEmoji(target.getStatusEmoji())
                .lastOnline(target.getLastOnline())
                .createdAt(target.getCreatedAt())
                .friendsSince(friendsSince)
                .friendship(friendSummary)
                .badges(badgeService.getBadgesForUser(target))
                .build();
    }

    public UserLookupResponse findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(u -> UserLookupResponse.builder()
                        .userId(u.getUserId())
                        .username(u.getUsername())
                        .build())
                .orElse(null);
    }

    public ChannelUserSummary toChannelDto(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return toChannelDto(user);
    }

    public ChannelUserSummary toChannelDto(User user) {
        byte[] avatarBytes = readAvatarBytes(user);
        String avatarBase64 = avatarBytes != null ? Base64.getEncoder().encodeToString(avatarBytes) : null;

        return ChannelUserSummary.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .avatar(avatarBase64)
                .avatarImageFormat(resolveAvatarFormat(user, avatarBytes))
                .micEnabled(user.isMicEnabled())
                .speakerEnabled(user.isSpeakerEnabled())
                .build();
    }

    public UserStatusDto toStatusDto(User user) {
        return UserStatusDto.builder()
                .userId(user.getUserId())
                .status(user.getStatus())
                .build();
    }

    public List<UserBatchCacheResponse> getBatchAvatars(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return userIds.stream()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(u -> u != null)
                .map(u -> {
                    byte[] avatarBytes = readAvatarBytes(u);
                    String avatarBase64 = avatarBytes != null ? Base64.getEncoder().encodeToString(avatarBytes) : null;
                    return UserBatchCacheResponse.builder()
                            .userId(u.getUserId())
                            .username(u.getUsername())
                            .avatar(avatarBase64)
                            .avatarImageFormat(resolveAvatarFormat(u, avatarBytes))
                            .build();
                })
                .toList();
    }

    private byte[] readAvatarBytes(User user) {
        if (user.getAvatarFilename() == null) return null;
        try {
            String avatarPath = Paths.get(avatarsDir, user.getAvatarFilename()).toString();

            return Files.readAllBytes(Paths.get(avatarPath));
        } catch (Exception e) {
            log.warn("Failed to read avatar for user {}: {}", user.getUserId(), e.getMessage());
            return null;
        }
    }

    private String resolveAvatarFormat(User user, byte[] avatarBytes) {
        if (avatarBytes == null || user.getAvatarFilename() == null) return null;
        String ext = FilenameUtils.getExtension(user.getAvatarFilename());
        return ext.isEmpty() ? null : ext.toLowerCase();
    }
}