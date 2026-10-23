package com.ninimum.api.finance;

import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import com.ninimum.api.warehouse.WarehouseServiceAccess;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * K5 (TZ v11) "Qaytarish kerak": every customer still owed money, in one list.
 * A full refund is made by cancelling the payment in Payme Business; Payme's callback then marks the order REFUNDED
 * and the row leaves the list by itself. Payme can't return part of a payment, so a partial-return refund is paid
 * another way and recorded here (money place, date, note); the money place shows it as money out on the Pul page.
 */
@Service
public class RefundService {
    private static final String SINCE = "COALESCE((SELECT MIN(h.changed_at) FROM order_status_history h WHERE h.order_id=o.id AND h.to_status IN ('CANCELLED','RETURNING','RETURNED')" +
            " AND h.id>COALESCE((SELECT MAX(n.id) FROM order_status_history n WHERE n.order_id=o.id AND n.to_status NOT IN ('CANCELLED','RETURNING','RETURNED')),0)),o.cancelled_at,o.updated_at)";
    private static final String CUSTOMER = "TRIM(CONCAT(COALESCE(u.first_name,''),' ',COALESCE(u.last_name,''))) customer_name,u.phone_number customer_phone";
    private static final String PAYME_ID = "(SELECT p.provider_transaction_id FROM payments p WHERE p.order_id=o.id AND p.provider='PAYME' AND p.payme_perform_time>0 ORDER BY p.id DESC LIMIT 1) payme_id";
    private final JdbcTemplate jdbc;
    public RefundService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Map<String,Object> list() {
        var pending = new ArrayList<Map<String,Object>>();
        // Whole payment back: paid orders that were cancelled, failed or returned.
        pending.addAll(jdbc.queryForList("SELECT o.id order_id,o.order_number,o.status,'FULL' kind," +
                "COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.order_id=o.id AND p.payme_perform_time>0 AND COALESCE(p.payme_cancel_time,0)=0),o.total_price) amount," +
                "o.cancel_reason reason,DATE_FORMAT(" + SINCE + ",'%Y-%m-%dT%H:%i:%sZ') since," + CUSTOMER + "," + PAYME_ID +
                " FROM orders o LEFT JOIN users u ON u.id=o.user_id WHERE o.payment_status='PAID' AND o.status IN ('CANCELLED','RETURNING','RETURNED')"));
        // Part of the payment back: goods the customer handed back to the courier.
        pending.addAll(jdbc.queryForList("SELECT o.id order_id,o.order_number,o.status,'PARTIAL' kind,pr.refund_amount amount,pr.reason," +
                "DATE_FORMAT(pr.created_at,'%Y-%m-%dT%H:%i:%sZ') since," + CUSTOMER + "," + PAYME_ID +
                " FROM delivery_partial_returns pr JOIN orders o ON o.id=pr.order_id LEFT JOIN users u ON u.id=o.user_id" +
                " WHERE pr.refund_status='PENDING' AND pr.refund_amount>0 AND o.payment_status='PAID' AND o.status NOT IN ('CANCELLED','RETURNING','RETURNED')"));
        pending.sort(Comparator.comparing(r -> String.valueOf(r.get("since"))));
        var done = new ArrayList<Map<String,Object>>();
        long monthAgo = System.currentTimeMillis() - 60L * 24 * 3600 * 1000;
        done.addAll(jdbc.queryForList("SELECT o.id order_id,o.order_number,'FULL' kind,p.amount,'Payme' place,NULL note,p.payme_cancel_time refunded_at,NULL created_by," + CUSTOMER +
                " FROM payments p JOIN orders o ON o.id=p.order_id LEFT JOIN users u ON u.id=o.user_id WHERE p.provider='PAYME' AND p.payme_perform_time>0 AND p.payme_cancel_time>? ORDER BY p.payme_cancel_time DESC", monthAgo));
        try {
            done.addAll(jdbc.queryForList("SELECT o.id order_id,o.order_number,r.kind,r.amount,a.name place,r.note,DATE_FORMAT(r.refund_date,'%Y-%m-%d') refunded_at,r.created_by," + CUSTOMER +
                    " FROM customer_refunds r JOIN orders o ON o.id=r.order_id LEFT JOIN users u ON u.id=o.user_id LEFT JOIN finance_accounts a ON a.id=r.account_id WHERE r.created_at>DATE_SUB(UTC_TIMESTAMP(),INTERVAL 60 DAY) ORDER BY r.id DESC"));
        } catch (DataAccessException ex) { /* table not migrated yet */ }
        return Map.of("pending", pending, "done", done);
    }

    /** Records a partial-return refund paid outside Payme. */
    @Transactional
    public void recordPartial(long orderId, Map<String,Object> body) {
        long account = WarehouseServiceAccess.positive(body.get("account_id"));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM finance_accounts WHERE id=?", Integer.class, account) != 1) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        LocalDate day = FinanceService.date(body.get("refund_date"));
        if (day == null) day = FinanceService.today();
        if (day.isAfter(FinanceService.today())) throw new WarehouseException("FINANCE_FUTURE_DATE");
        PeriodClose.guard(day);
        String note = FinanceService.text(body.get("note"), 500, true);
        var rows = jdbc.queryForList("SELECT pr.refund_amount FROM delivery_partial_returns pr JOIN orders o ON o.id=pr.order_id WHERE pr.order_id=? AND pr.refund_status='PENDING' AND pr.refund_amount>0 AND o.payment_status='PAID' FOR UPDATE", orderId);
        if (rows.isEmpty()) throw new WarehouseException("REFUND_NOT_PENDING");
        BigDecimal amount = (BigDecimal) rows.get(0).get("refund_amount");
        jdbc.update("INSERT INTO customer_refunds(order_id,kind,amount,account_id,refund_date,note,created_by,created_at) VALUES(?,'PARTIAL',?,?,?,?,?,UTC_TIMESTAMP())", orderId, amount, account, day, note, WarehouseService.actor());
        jdbc.update("UPDATE delivery_partial_returns SET refund_status='MANUAL_REFUND' WHERE order_id=? AND refund_status='PENDING'", orderId);
    }
}
