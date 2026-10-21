package com.ninimum.api.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * M11: customer phone numbers are stored in E.164 (+998XXXXXXXXX).
 * Rows written before M11 may still hold 998XXXXXXXXX or XXXXXXXXX, so lookups try every form.
 */
public final class PhoneNumbers {
    private PhoneNumbers() {}

    /** Returns the E.164 form (+998XXXXXXXXX, or +country for a foreign number), or null when the value is not a phone number. */
    public static String normalize(String value) {
        if (value == null) return null;
        String text = value.trim().replaceAll("[\\s\\-().]", "");
        boolean plus = text.startsWith("+");
        String digits = plus ? text.substring(1) : text;
        if (!digits.matches("\\d{8,15}")) return null;
        if (!plus && digits.startsWith("00")) { digits = digits.substring(2); plus = true; }
        if (!plus && digits.length() == 9) return "+998" + digits;
        if (digits.startsWith("998")) return digits.length() == 12 ? "+" + digits : null;
        return plus && digits.length() >= 8 ? "+" + digits : null;
    }

    /** Every stored form the number may have: E.164 first, then 998XXXXXXXXX, XXXXXXXXX and the value as typed. */
    public static List<String> variants(String value) {
        var forms = new LinkedHashSet<String>();
        String e164 = normalize(value);
        if (e164 != null) {
            forms.add(e164);
            forms.add(e164.substring(1));
            if (e164.startsWith("+998")) forms.add(e164.substring(4));
        }
        if (value != null && !value.isBlank()) forms.add(value.trim());
        return new ArrayList<>(forms);
    }

    /** The form the SMS provider expects (998XXXXXXXXX, no plus). */
    public static String sms(String value) {
        String e164 = normalize(value);
        return e164 == null ? (value == null ? null : value.trim()) : e164.substring(1);
    }
}
