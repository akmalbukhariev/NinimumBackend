package com.ninimum.api.user.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Customer deletes their account. Orders and payments are money records and stay, so the account row is
 * wiped instead of removed: name, phone, address and password go, and the number is free to register again.
 * Cart, favourites, cards, children, unused addresses and the promo code are removed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletion {
    /** A paid order still on its way: the courier needs the customer's phone, so deletion waits. */
    public static class ActiveOrder extends Exception {
        public ActiveOrder() { super("ACCOUNT_HAS_ACTIVE_ORDER"); }
    }

    private final JdbcTemplate jdbc;

    private void optional(String sql, Object... args) {
        try { jdbc.update(sql, args); } catch (DataAccessException ex) { log.warn("Account deletion step skipped: {}", ex.getMessage()); }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(long user, String reasons) throws ActiveOrder {
        var phone = jdbc.queryForList("SELECT phone_number FROM users WHERE id=? AND is_active=1 FOR UPDATE", String.class, user);
        if (phone.isEmpty()) return false;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=? AND payment_status='PAID' AND status NOT IN ('DELIVERED','CANCELLED','RETURNED')", Integer.class, user) > 0)
            throw new ActiveOrder();
        if (reasons != null && !reasons.isEmpty()) optional("INSERT INTO user_delete_feedback(user_id,reasons,created_at) VALUES(?,?,UTC_TIMESTAMP())", user, reasons);
        optional("INSERT IGNORE INTO account_deletions(user_id,phone_hash,deleted_at) VALUES(?,SHA2(?,256),UTC_TIMESTAMP())", user, phone.get(0));
        for (String table : List.of("cart_items", "favorites", "children", "payment_cards", "user_qr_codes", "recently_viewed_products", "referral_codes", "trusted_devices"))
            optional("DELETE FROM " + table + " WHERE user_id=?", user);
        optional("DELETE FROM user_addresses WHERE user_id=? AND address_id NOT IN (SELECT address_id FROM orders WHERE address_id IS NOT NULL)", user);
        optional("DELETE FROM addresses WHERE user_id=? AND id NOT IN (SELECT legacy_address_id FROM orders WHERE legacy_address_id IS NOT NULL)", user);
        jdbc.update("UPDATE user_tariffs SET status='CANCELLED',cancelled_at=COALESCE(cancelled_at,NOW()),updated_at=NOW() WHERE user_id=? AND status='PENDING'", user);
        jdbc.update("UPDATE user_tariffs SET status='EXPIRED',end_date=LEAST(COALESCE(end_date,NOW()),NOW()),auto_renew=0,updated_at=NOW() WHERE user_id=? AND status='ACTIVE'", user);
        return jdbc.update("UPDATE users SET phone_number=CONCAT('deleted-',id),first_name='O‘chirilgan mijoz',last_name=NULL,address=NULL," +
                "location_latitude=NULL,location_longitude=NULL,email=NULL,profile_image_url=NULL,birth_date=NULL,gender=NULL,password='',token_mb=NULL," +
                "status='DELETED',is_active=0,updated_at=NOW() WHERE id=?", user) == 1;
    }
}
