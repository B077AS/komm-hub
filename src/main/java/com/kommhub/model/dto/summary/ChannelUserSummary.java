package com.kommhub.model.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChannelUserSummary {

    private UUID userId;
    private String username;
    private String avatar;
    private String avatarImageFormat;
    private boolean micEnabled;
    private boolean speakerEnabled;
}