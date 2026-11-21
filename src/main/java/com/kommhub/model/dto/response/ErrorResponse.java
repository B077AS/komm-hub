package com.kommhub.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String message;
    private String error;
    private Integer status;

    /** Machine-readable reason, set only where a client has to react to it (e.g. SETUP_CODE_INVALID); null otherwise. */
    private String code;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static ResponseEntity<ErrorResponse> of(HttpStatus status, String message) {
        return of(status, null, message);
    }

    public static ResponseEntity<ErrorResponse> of(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                ErrorResponse.builder()
                        .status(status.value())
                        .error(status.getReasonPhrase())
                        .code(code)
                        .message(message)
                        .build()
        );
    }
}