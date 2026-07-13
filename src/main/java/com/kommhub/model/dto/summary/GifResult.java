package com.kommhub.model.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GifResult {
    private String id;
    private String title;
    private String previewUrl;  // still used as poster/thumbnail
    private String fullUrl;
    private String previewMp4Url;  // NEW - for the popup grid
    private String fullMp4Url;     // NEW - for sending in chat
    private int width;
    private int height;
}