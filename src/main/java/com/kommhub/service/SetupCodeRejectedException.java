package com.kommhub.service;

/**
 * A server presented a verification code the hub will not accept. Carries a machine-readable {@link #getCode()} next to
 * the human message so the server can tell "your code is wrong" (stop and ask for a new one) from a hub that is merely
 * unreachable (try again later). Extends {@link IllegalArgumentException} so anything that already catches that keeps
 * working.
 */
public class SetupCodeRejectedException extends IllegalArgumentException {

    /** The code is unknown: mistyped, or already used (a code works once and is cleared when the server activates). */
    public static final String INVALID = "SETUP_CODE_INVALID";
    /** The installation is past activation although its code was still on record. */
    public static final String ALREADY_VERIFIED = "INSTALLATION_ALREADY_VERIFIED";

    private final String code;

    public SetupCodeRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
