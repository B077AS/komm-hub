package com.kommhub.websocket.messages.payloads;

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
public class MemberStatusUpdatedPayload {
    private UUID userId;
    private User.UserStatus status;
}
