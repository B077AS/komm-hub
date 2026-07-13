package com.kommhub.model.dto.request;

import com.kommhub.model.db.User;
import lombok.Data;

@Data
public class StatusUpdateRequest {
    private User.UserStatus status;
}
