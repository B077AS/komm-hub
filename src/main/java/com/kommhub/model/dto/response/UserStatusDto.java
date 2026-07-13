package com.kommhub.model.dto.response;

import com.kommhub.model.db.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusDto {
    private UUID userId;
    private User.UserStatus status;
}
