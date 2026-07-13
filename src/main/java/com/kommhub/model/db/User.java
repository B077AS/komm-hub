package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User implements UserDetails{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Column(name = "username", nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "avatar_filename")
    private String avatarFilename;

    // Live/effective status broadcast to others and returned by fetches.
    // Forced to OFFLINE while the user has no active WebSocket session.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.OFFLINE;

    // The status the user chose for themselves (ONLINE/AWAY/DO_NOT_DISTURB/INVISIBLE).
    // Persists across disconnects so it can be restored when they reconnect.
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_status")
    @Builder.Default
    private UserStatus preferredStatus = UserStatus.ONLINE;

    @Column(name = "status_message")
    private String statusMessage;

    // Codepoint "unified" string of a single emoji (e.g. "1F600"), the same
    // representation used for message reactions, rendered client-side.
    @Column(name = "status_emoji")
    private String statusEmoji;

    @Column(name = "last_online")
    private LocalDateTime lastOnline;

    @Column(name = "mic_enabled")
    @Builder.Default
    private boolean micEnabled = true;

    @Column(name = "speaker_enabled")
    @Builder.Default
    private boolean speakerEnabled = true;

    // Who is allowed to start/continue a DM conversation with this user
    @Enumerated(EnumType.STRING)
    @Column(name = "dm_privacy", nullable = false)
    @Builder.Default
    private DmPrivacy dmPrivacy = DmPrivacy.EVERYONE;

    // Existing users default to true via column default; new users start false until verified
    @Column(name = "email_verified")
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return emailVerified;
    }

    public enum Role {
        USER,
        SUPER_ADMIN
    }

    /** Controls who may send this user a direct message. */
    public enum DmPrivacy {
        EVERYONE,
        FRIENDS_ONLY,
        NONE
    }

    @Getter
    public enum UserStatus {
        ONLINE("ONLINE"),
        AWAY("AWAY"),
        DO_NOT_DISTURB("DO NOT DISTURB"),
        INVISIBLE("INVISIBLE"),
        OFFLINE("OFFLINE");

        private final String value;

        UserStatus(String value) {
            this.value = value;
        }

    }
}