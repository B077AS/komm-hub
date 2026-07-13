package com.kommhub.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBatchCacheResponse {
    private UUID userId;
    private String username;
    private String avatar;
    private String avatarImageFormat;
}
