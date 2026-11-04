package com.ninimum.api.message;

import com.ninimum.api.common.PhoneNumbers;
import com.ninimum.api.message.service.IMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMS codes are kept and checked on the server only; the app never receives them. A code is valid for 5 minutes and
 * 5 wrong tries. Sending is limited (per number: 1 a minute, 5 a day; all numbers: 300 an hour) to protect the Eskiz balance.
 * Kept in memory: a server restart only cancels codes still waiting to be typed.
 */
@Service
public class SmsCodes {
    public interface Deliver { String send(String phone, boolean login) throws Exception; }
    public static class Limited extends Exception { public Limited(String code) { super(code); } }

    static final long TTL = 5 * 60_000L, MINUTE = 60_000L, HOUR = 3_600_000L, DAY = 86_400_000L;
    static final int PER_DAY = 5, ALL_PER_HOUR = 300, TRIES = 5;
    private record Entry(String code, long expires, int tries) {}

    private final Deliver deliver;
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<Long>> sent = new ConcurrentHashMap<>();
    private final ArrayDeque<Long> all = new ArrayDeque<>();

    @Autowired
    public SmsCodes(IMessageService messages) { this(messages::deliverCode); }
    public SmsCodes(Deliver deliver) { this.deliver = deliver; }

    public static String message(String code) {
        return switch (code) {
            case "SMS_TOO_SOON" -> "Kodni qayta so‘rashdan oldin 1 daqiqa kuting.";
            case "SMS_DAILY_LIMIT" -> "Bugun bu raqamga juda ko‘p kod yuborildi. Ertaga qayta urinib ko‘ring.";
            case "SMS_BUSY" -> "Hozir SMS yuborib bo‘lmaydi. Birozdan keyin qayta urinib ko‘ring.";
            case "SMS_CODE_INVALID" -> "SMS kod noto‘g‘ri yoki eskirgan.";
            default -> "SMS yuborilmadi. Birozdan keyin qayta urinib ko‘ring.";
        };
    }

    /** Sends a new code (login text for an existing account). Throws Limited, or Exception when Eskiz didn't take it. */
    public void send(String phone, boolean login) throws Exception { send(phone, login, System.currentTimeMillis()); }
    void send(String phone, boolean login, long now) throws Exception {
        String key = PhoneNumbers.normalize(phone);
        if (key == null) throw new IllegalArgumentException("Invalid phone number");
        reserve(key, now);
        String code = deliver.send(key, login);
        if (code == null || code.isBlank()) throw new IllegalStateException("SMS was not sent");
        codes.put(key, new Entry(code, System.currentTimeMillis() + TTL, 0));
    }

    private void reserve(String key, long now) throws Limited {
        synchronized (all) {
            while (!all.isEmpty() && now - all.peekFirst() > HOUR) all.pollFirst();
            var times = sent.computeIfAbsent(key, k -> new ArrayDeque<>());
            while (!times.isEmpty() && now - times.peekFirst() > DAY) times.pollFirst();
            if (!times.isEmpty() && now - times.peekLast() < MINUTE) throw new Limited("SMS_TOO_SOON");
            if (times.size() >= PER_DAY) throw new Limited("SMS_DAILY_LIMIT");
            if (all.size() >= ALL_PER_HOUR) throw new Limited("SMS_BUSY");
            times.addLast(now); all.addLast(now);
        }
    }

    /** True when the code is right; it stays valid for the action that follows (registration needs it again). */
    public boolean check(String phone, String code) { return match(phone, code, false); }
    /** True when the code is right; it can't be used again. */
    public boolean consume(String phone, String code) { return match(phone, code, true); }

    private boolean match(String phone, String code, boolean use) {
        String key = PhoneNumbers.normalize(phone);
        if (key == null || code == null || code.isBlank()) return false;
        synchronized (codes) {
            Entry e = codes.get(key);
            if (e == null || System.currentTimeMillis() > e.expires()) { codes.remove(key); return false; }
            if (MessageDigest.isEqual(e.code().getBytes(StandardCharsets.UTF_8), code.trim().getBytes(StandardCharsets.UTF_8))) {
                if (use) codes.remove(key);
                return true;
            }
            if (e.tries() + 1 >= TRIES) codes.remove(key); else codes.put(key, new Entry(e.code(), e.expires(), e.tries() + 1));
            return false;
        }
    }
}
