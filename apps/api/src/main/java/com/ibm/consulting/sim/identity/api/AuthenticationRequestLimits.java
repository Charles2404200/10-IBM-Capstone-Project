package com.ibm.consulting.sim.identity.api;

final class AuthenticationRequestLimits {
    static final int EMAIL_MAX_LENGTH = 255;
    static final int PASSWORD_MAX_LENGTH = 128;
    // Current opaque credentials are 80 characters (UUID selector + 256-bit
    // base64url secret); the headroom permits a future secret-size increase.
    static final int CREDENTIAL_TOKEN_MAX_LENGTH = 128;

    private AuthenticationRequestLimits() {}
}
