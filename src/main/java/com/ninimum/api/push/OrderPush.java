package com.ninimum.api.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * Order notifications, read from the status history so every path (apps, taxi, web admin) is covered.
 * Customer: paid, packed, on the way, delivered, cancelled. Warehouse: new paid order. Courier: packed courier order.
 */
@Slf4j
@Service
public class OrderPush {
    private final JdbcTemplate jdbc;
    private final Push push;
    private boolean schemaWarning;

    public OrderPush(JdbcTemplate jdbc, Push push) { this.jdbc = jdbc; this.push = push; }

    @Scheduled(fixedDelayString = "${push.orders.interval-ms:20000}", initialDelayString = "${push.orders.initial-delay-ms:60000}")
    public synchronized void tick() {
        if (!push.enabled()) return;
        try { scan(); schemaWarning = false; }
        catch (DataAccessException ex) { if (!schemaWarning) log.warn("Order push unavailable. Check the push migration."); schemaWarning = true; }
    }

    private static String customerText(String status, boolean taxi) {
        return switch (status) {
            case "CONFIRMED" -> "To‘lov qabul qilindi. Buyurtmangizni tayyorlaymiz.";
            case "READY" -> taxi ? "Buyurtmangiz tayyor. Taksi chaqirilmoqda." : "Buyurtmangiz tayyor. Kuryer tez orada olib ketadi.";
            case "ON_THE_WAY" -> taxi ? "Buyurtmangiz taksi orqali yo‘lga chiqdi." : "Buyurtmangiz yo‘lga chiqdi. Kuryer tez orada yetkazib beradi.";
            case "DELIVERED" -> "Buyurtmangiz yetkazildi. Xaridingiz uchun rahmat!";
            case "CANCELLED" -> "Buyurtmangiz bekor qilindi.";
            default -> null;
        };
    }

    // Only recent changes, so turning push on doesn't announce old orders.
    void scan() {
        for (var row : jdbc.queryForList("SELECT h.order_id,h.to_status,o.user_id,o.delivery_method FROM order_status_history h JOIN orders o ON o.id=h.order_id " +
                "WHERE h.changed_at>=UTC_TIMESTAMP()-INTERVAL 10 MINUTE AND h.to_status IN ('CONFIRMED','READY','ON_THE_WAY','DELIVERED','CANCELLED') ORDER BY h.id")) {
            long order = ((Number) row.get("order_id")).longValue();
            String status = (String) row.get("to_status");
            if (jdbc.update("INSERT IGNORE INTO push_log(order_id,kind,created_at) VALUES(?,?,UTC_TIMESTAMP())", order, status) == 0) continue;
            boolean taxi = "TAXI".equals(row.get("delivery_method"));
            String number = String.format("#%06d", order);
            Map<String, String> data = Map.of("order_id", String.valueOf(order), "status", status);
            push.send(Push.CUSTOMER, String.valueOf(row.get("user_id")), "Buyurtma " + number, customerText(status, taxi), data);
            if ("CONFIRMED".equals(status)) push.send(Push.WAREHOUSE, null, "Yangi buyurtma " + number, "To‘langan buyurtma navbatda. Tayyorlashni boshlang.", data);
            if ("READY".equals(status) && !taxi) push.send(Push.COURIER, null, "Yetkazishga tayyor " + number, "Buyurtma qadoqlandi va olib ketishni kutmoqda.", data);
        }
    }
}
