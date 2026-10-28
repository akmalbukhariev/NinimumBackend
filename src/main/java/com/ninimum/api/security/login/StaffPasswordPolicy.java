package com.ninimum.api.security.login;
import java.nio.charset.StandardCharsets;
/** Existing credentials remain valid; this policy applies to newly assigned staff passwords. */
public final class StaffPasswordPolicy {
    private StaffPasswordPolicy() {}
    public static String require(Object input) {
        // No strength rules (owner decision 2026-10-28): any non-empty password; bcrypt reads only 72 bytes.
        if(!(input instanceof String password)||password.isEmpty()||password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Password is required (maximum 72 UTF-8 bytes).");
        return password;
    }
}
