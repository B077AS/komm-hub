package com.kommhub.model.dto.summary;

import com.kommhub.model.db.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
    public class MainUserSummary {

    private UUID userId;
    private String username;
    private String email;
    private User.UserStatus status;
    private String statusMessage;
    private String statusEmoji;
    private String avatar;
    private String avatarImageFormat;
    private boolean micEnabled;
    private boolean speakerEnabled;
    private User.DmPrivacy dmPrivacy;
    private User.Role role;
    private List<BadgeSummary> badges;
}