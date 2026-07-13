package com.kommhub.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberStatusPageResponse {
    private List<UserStatusDto> members;
    private long total;
    private int page;
    private int size;
}
