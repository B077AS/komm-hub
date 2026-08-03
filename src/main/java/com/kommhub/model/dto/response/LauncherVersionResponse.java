package com.kommhub.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LauncherVersionResponse {
    private String version;
    private String downloadUrl;
    private String sha256;
}
