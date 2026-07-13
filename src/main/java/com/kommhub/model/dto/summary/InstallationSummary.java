package com.kommhub.model.dto.summary;

import com.kommhub.model.db.Installation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallationSummary {

    private UUID installationId;
    private String installationName;
    private int installationPort;
    private int hostedServersCount;
    private Installation.InstallationStatus status;
    private String ipAddress;
    private InstallationRole role;

    public enum InstallationRole {
        OWNER, MEMBER
    }
}