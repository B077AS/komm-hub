package com.kommhub.model.dto.summary;

import com.kommhub.model.db.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {

    private UUID userId;
    private String username;
    private User.UserStatus status;
    private String statusMessage;
    private String statusEmoji;
    private LocalDateTime lastOnline;
    private LocalDateTime createdAt;
    private LocalDateTime friendsSince;
    private FriendSummary friendship;
    private List<BadgeSummary> badges;
}
