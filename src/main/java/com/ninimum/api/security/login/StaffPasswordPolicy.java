package com.ninimum.api.security.login;
import java.nio.charset.StandardCharsets;
/** Existing credentials remain valid; this policy applies to newly assigned staff passwords. */
public final class StaffPasswordPolicy {
    private StaffPasswordPolicy() {}
    public static String require(Object input) {
        if(!(input instanceof String password)||password.length()<10||password.getBytes(StandardCharsets.UTF_8).length>72
            ||password.chars().noneMatch(Character::isUpperCase)||password.chars().noneMatch(Character::isLowerCase)
            ||password.chars().noneMatch(Character::isDigit)||password.chars().noneMatch(c->!Character.isLetterOrDigit(c)&&!Character.isWhitespace(c)))
            throw new IllegalArgumentException("Password must contain at least 10 characters, uppercase, lowercase, a number and a symbol (maximum 72 UTF-8 bytes).");
        return password;
    }
}
