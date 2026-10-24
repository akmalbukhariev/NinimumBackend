package com.ninimum.api.ordersafety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * K7 (TZ v11): a new problem alert also goes to Telegram (bot "Ninimum Nazorat").
 * Off unless TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_IDS are set on the server. Sending runs in the background,
 * so a slow or blocked Telegram never delays the order timers; failures are logged without the token.
 */
@Slf4j
@Component
public class TelegramNotifier {
    static final Map<String,String> LABELS = Map.ofEntries(
        Map.entry("COURIER_REASSIGN", "Kuryer 3 ta taklifni qabul qilmadi"), Map.entry("PREPARATION_QUEUE", "Tayyorlash navbati kechikdi"),
        Map.entry("PREPARATION_DELAY", "Tayyorlash kechikdi"), Map.entry("COURIER_WAIT", "Kuryer biriktirilmagan"),
        Map.entry("COURIER_PICKUP", "Kuryer olib ketishi kechikdi"), Map.entry("DELIVERY_DELAY", "Yetkazish kechikdi"),
        Map.entry("REFUND_PENDING", "Pulni qaytarish kutilmoqda"), Map.entry("UNANSWERED_QUESTION", "Savol yoki shikoyat javobsiz"),
        Map.entry("STOCK_EXPIRED", "Partiya muddati o‘tgan"), Map.entry("STOCK_EXPIRING", "Partiya muddati yaqin"));
    private final JdbcTemplate jdbc;
    private final String token, adminUrl;
    private final List<String> chats;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "telegram-alerts"); t.setDaemon(true); return t; });

    public TelegramNotifier(JdbcTemplate jdbc, @Value("${TELEGRAM_BOT_TOKEN:}") String token, @Value("${TELEGRAM_CHAT_IDS:}") String chats,
                            @Value("${TELEGRAM_ADMIN_URL:}") String adminUrl) {
        this.jdbc = jdbc; this.token = token.trim(); this.adminUrl = adminUrl.trim().replaceAll("/+$", "");
        this.chats = Arrays.stream(chats.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    public boolean enabled() { return !token.isEmpty() && !chats.isEmpty(); }

    /** Called after the timer scan committed, once per newly opened alert. */
    public void opened(String type, long id, String kind, long elapsedMinutes) {
        if (!enabled()) return;
        String text;
        try { text = message(type, id, kind, elapsedMinutes); } catch (RuntimeException ex) { log.warn("Telegram alert text failed for {} {}", type, id); return; }
        for (String chat : chats) sender.submit(() -> send(chat, text));
    }

    String message(String type, long id, String kind, long elapsedMinutes) {
        var out = new StringBuilder("⚠️ ").append(LABELS.getOrDefault(kind, kind)).append('\n');
        String link = null;
        if ("ORDER".equals(type)) {
            var rows = jdbc.queryForList("SELECT order_number,total_price FROM orders WHERE id=?", id);
            out.append("Buyurtma #").append(id);
            if (!rows.isEmpty()) out.append(" (").append(rows.get(0).get("order_number")).append(')');
            link = "/orders?order=" + id;
        } else if ("BATCH".equals(type)) {
            var rows = jdbc.queryForList("SELECT p.name,l.code,b.physical_quantity,DATE_FORMAT(b.expiry_date,'%d.%m.%Y') expiry FROM inventory_batches b JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id WHERE b.id=?", id);
            if (!rows.isEmpty()) { var r = rows.get(0); out.append(r.get("name")).append(" · ").append(r.get("code")).append(" · ").append(r.get("physical_quantity")).append(" dona · muddat ").append(r.get("expiry")); }
            link = "/inventory?tab=expiry";
        } else if ("QUESTION".equals(type)) {
            out.append("Savol #").append(id);
            link = "/reviews?tab=questions";
        } else out.append(type).append(" #").append(id);
        if (elapsedMinutes > 0 && !kind.startsWith("STOCK_")) out.append("\nKutmoqda: ").append(elapsedMinutes >= 120 ? (elapsedMinutes / 60) + " soat" : elapsedMinutes + " daqiqa");
        if (link != null && !adminUrl.isEmpty()) out.append('\n').append(adminUrl).append(link);
        return out.toString();
    }

    /** A test message from Settings, so the owner can check the bot without waiting for a real problem. */
    public boolean test() {
        if (!enabled()) return false;
        boolean ok = true;
        for (String chat : chats) ok &= send(chat, "✅ Ninimum Nazorat ulandi. Muammolar haqida xabarlar shu yerga keladi.");
        return ok;
    }

    private boolean send(String chat, String text) {
        try {
            String body = "chat_id=" + URLEncoder.encode(chat, StandardCharsets.UTF_8) + "&disable_web_page_preview=true&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage")).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != 200) log.warn("Telegram alert to chat {} returned HTTP {}", chat, status);
            return status == 200;
        } catch (Exception ex) {
            log.warn("Telegram alert to chat {} failed: {}", chat, ex.getClass().getSimpleName());
            return false;
        }
    }
}
