package com.kommhub.model.dto.request;

import lombok.Data;

@Data
public class InstallationValidationRequest {
    private String setupToken;
    private String csr;
}