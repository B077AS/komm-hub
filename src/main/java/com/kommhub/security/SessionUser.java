package com.kommhub.security;

import lombok.Builder;
import lombok.Value;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Value
@Builder
public class SessionUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String username;
}
