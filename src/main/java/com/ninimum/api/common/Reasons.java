package com.ninimum.api.common;

/** TZ v13 item 7: a cancel or failure reason must say something: at least 10 letters ("iii" is refused). */
public final class Reasons {
    private Reasons() {}
    public static final int MIN_LETTERS = 10;
    public static final String CODE = "REASON_TOO_SHORT";
    public static final String MESSAGE = "Sababni aniqroq yozing (kamida 10 ta harf).";
    public static boolean ok(String reason) {
        if (reason == null) return false;
        return reason.codePoints().filter(Character::isLetter).count() >= MIN_LETTERS;
    }
}
