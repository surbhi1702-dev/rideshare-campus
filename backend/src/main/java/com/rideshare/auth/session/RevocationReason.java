package com.rideshare.auth.session;

public enum RevocationReason {
    /** Normal: exchanged for a newer token. */
    ROTATED,
    LOGOUT,
    /** An already-rotated token came back: assume it was stolen and end the session. */
    REUSE_DETECTED
}
