package com.kommhub.model.dto.response;

import com.kommhub.model.dto.summary.ServerMemberDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMemberPageResponse {
    private List<ServerMemberDto> members;
    private long total;
    private int page;
    private int size;
}
