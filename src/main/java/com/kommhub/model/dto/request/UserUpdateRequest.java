package com.kommhub.model.dto.request;

import com.kommhub.model.db.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserUpdateRequest {

    private String username;
    private String email;
    private User.UserStatus status;
    private String statusMessage;
    private String statusEmoji;
    private String avatar;
    private String avatarImageFormat;
    private User.DmPrivacy dmPrivacy;
}
