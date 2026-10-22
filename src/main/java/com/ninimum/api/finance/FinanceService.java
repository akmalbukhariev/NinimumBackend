package com.ninimum.api.finance;

import com.ninimum.api.admin.access.StaffAccess;
import com.ninimum.api.admin.access.StrictRules;
import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import com.ninimum.api.warehouse.WarehouseServiceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

/**
 * TZ v10 §9: money places, expenses, transfers, manual income, cash counts, money movement and the monthly result.
 * Only the owner and staff with the money permissions reach it (see StaffAccess rules for /admin/finance/**).
 */
@Service
@RequiredArgsConstructor
public class FinanceService {
    private final JdbcTemplate jdbc;
    static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    // Payme times are epoch milliseconds; +18000 s turns them into Tashkent local time.
    private static final String PAYME_DAY = "DATE(DATE_ADD('1970-01-01',INTERVAL (%s DIV 1000 + 18000) SECOND))";
    /** Every money movement as (account_id, d, amount, kind). The opening balance is the start of its day; earlier movements are ignored. */
    private static final String MOVEMENTS =
        "SELECT m.account_id,m.d,m.amount,m.kind FROM (" +
        " SELECT id account_id,opening_date d,opening_balance amount,'OPENING' kind FROM finance_accounts WHERE opening_date IS NOT NULL" +
        " UNION ALL SELECT (SELECT id FROM finance_accounts WHERE code='ONLINE')," + String.format(PAYME_DAY, "p.payme_perform_time") + ",p.amount,'PAYME' FROM payments p WHERE p.provider='PAYME' AND p.payme_perform_time>0" +
        " UNION ALL SELECT (SELECT id FROM finance_accounts WHERE code='ONLINE')," + String.format(PAYME_DAY, "p.payme_cancel_time") + ",-p.amount,'PAYME_REFUND' FROM payments p WHERE p.provider='PAYME' AND p.payme_perform_time>0 AND p.payme_cancel_time>0" +
        " UNION ALL SELECT account_id,doc_date,amount,'INCOME' FROM finance_documents WHERE kind='INCOME' AND status='POSTED'" +
        " UNION ALL SELECT account_id,doc_date,-amount,'EXPENSE' FROM finance_documents WHERE kind='EXPENSE' AND status='POSTED'" +
        " UNION ALL SELECT account_id,doc_date,-amount,'TRANSFER_OUT' FROM finance_documents WHERE kind='TRANSFER' AND status='POSTED'" +
        " UNION ALL SELECT to_account_id,doc_date,amount,'TRANSFER_IN' FROM finance_documents WHERE kind='TRANSFER' AND status='POSTED'" +
        " UNION ALL SELECT account_id,paid_date,-amount,'SUPPLIER' FROM supplier_payment_records WHERE status='RECORDED' AND account_id IS NOT NULL" +
        ") m JOIN finance_accounts a ON a.id=m.account_id WHERE a.opening_date IS NULL OR m.d>=a.opening_date";

    static LocalDate today() { return LocalDate.now(TASHKENT); }

    public BigDecimal balance(long account, LocalDate at) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM (" + MOVEMENTS + ") x WHERE account_id=? AND d<=?", BigDecimal.class, account, at);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> accounts() {
        var rows = jdbc.queryForList("SELECT id,code,name,opening_balance,DATE_FORMAT(opening_date,'%Y-%m-%d') opening_date FROM finance_accounts ORDER BY id");
        for (var r : rows) r.put("balance", balance(((Number) r.get("id")).longValue(), today()));
        return rows;
    }

    @Transactional
    public void setOpening(long id, Map<String,Object> body) {
        BigDecimal amount = money(body.get("opening_balance"), true);
        LocalDate date = date(body.get("opening_date"));
        // Q-16: the opening balance counts from its date, so neither the old nor the new date may be in a closed period.
        PeriodClose.guard(date);
        for (var old : jdbc.queryForList("SELECT opening_date FROM finance_accounts WHERE id=?", LocalDate.class, id)) PeriodClose.guard(old);
        if (jdbc.update("UPDATE finance_accounts SET opening_balance=?,opening_date=?,updated_by=?,updated_at=UTC_TIMESTAMP() WHERE id=?", amount, date, WarehouseService.actor(), id) != 1)
            throw new WarehouseException("WAREHOUSE_NOT_FOUND");
    }

    public List<Map<String,Object>> expenseTypes() { return jdbc.queryForList("SELECT id,name,is_active FROM expense_types ORDER BY is_active DESC,name"); }

    /** The owner keeps the list of expense types (TZ v10 §9). */
    @Transactional
    public void saveExpenseType(Long id, Map<String,Object> body) {
        if (!StaffAccess.isOwner()) throw new org.springframework.security.access.AccessDeniedException("Owner only");
        String name = text(body.get("name"), 100, true);
        boolean active = !Boolean.FALSE.equals(body.get("is_active"));
        try {
            if (id == null) jdbc.update("INSERT INTO expense_types(name,is_active) VALUES(?,?)", name, active);
            else if (jdbc.update("UPDATE expense_types SET name=?,is_active=? WHERE id=?", name, active, id) != 1) throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        } catch (org.springframework.dao.DuplicateKeyException ex) { throw new WarehouseException("FINANCE_DUPLICATE_TYPE"); }
    }

    @Transactional
    public long create(Map<String,Object> body) {
        String kind = text(body.get("kind"), 20, true);
        if (!Set.of("EXPENSE", "INCOME", "TRANSFER").contains(kind)) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        LocalDate date = date(body.get("doc_date"));
        if (date == null) date = today();
        if (date.isAfter(today())) throw new WarehouseException("FINANCE_FUTURE_DATE");
        PeriodClose.guard(date);
        BigDecimal amount = money(body.get("amount"), false);
        long account = account(body.get("account_id"));
        Long to = null, type = null;
        byte[] photo = null;
        if ("TRANSFER".equals(kind)) {
            to = account(body.get("to_account_id"));
            if (to == account) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        }
        if ("EXPENSE".equals(kind)) {
            type = WarehouseServiceAccess.positive(body.get("expense_type_id"));
            if (jdbc.queryForObject("SELECT COUNT(*) FROM expense_types WHERE id=? AND is_active=1", Integer.class, type) != 1) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            photo = WarehouseServiceAccess.image(body.get("receipt_photo_base64"));
            // Q-18: an expense needs a receipt photo (staff on, owner off by default).
            if (photo == null && StrictRules.on("Q-18")) throw new WarehouseException("FINANCE_PHOTO_REQUIRED");
        }
        Long toAccount = to, expenseType = type;
        byte[] receipt = photo;
        LocalDate day = date;
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(c -> {
            var p = c.prepareStatement("INSERT INTO finance_documents(kind,doc_date,amount,account_id,to_account_id,expense_type_id,counterparty,note,receipt_photo,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())", java.sql.Statement.RETURN_GENERATED_KEYS);
            Object[] args = {kind, java.sql.Date.valueOf(day), amount, account, toAccount, expenseType, text(body.get("counterparty"), 200, false), text(body.get("note"), 500, false), receipt, WarehouseService.actor()};
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            return p;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    @Transactional
    public void reverse(long id, String reason) {
        reason = text(reason, 500, true);
        var day = jdbc.queryForList("SELECT doc_date FROM finance_documents WHERE id=? FOR UPDATE", java.time.LocalDate.class, id);
        if (!day.isEmpty()) PeriodClose.guard(day.get(0));
        if (jdbc.update("UPDATE finance_documents SET status='REVERSED',reversed_by=?,reversed_at=UTC_TIMESTAMP(),reversal_reason=? WHERE id=? AND status='POSTED'", WarehouseService.actor(), reason, id) != 1)
            throw new WarehouseException("WAREHOUSE_INVALID_STATE");
    }

    @Transactional(readOnly = true)
    public Map<String,Object> documents(String kind, String from, String to, int page) {
        var where = new StringBuilder(" WHERE 1=1");
        var args = new ArrayList<Object>();
        if (kind != null && !kind.isBlank()) { where.append(" AND d.kind=?"); args.add(kind); }
        if (date(from) != null) { where.append(" AND d.doc_date>=?"); args.add(date(from)); }
        if (date(to) != null) { where.append(" AND d.doc_date<=?"); args.add(date(to)); }
        int p = Math.max(1, page);
        var list = new ArrayList<>(args); list.add((p - 1) * 50);
        var items = jdbc.queryForList("SELECT d.id,d.kind,DATE_FORMAT(d.doc_date,'%Y-%m-%d') doc_date,d.amount,d.account_id,a.name account_name,d.to_account_id,t.name to_account_name," +
                "d.expense_type_id,e.name expense_type,d.counterparty,d.note,d.status,d.created_by,DATE_FORMAT(d.created_at,'%Y-%m-%dT%H:%i:%sZ') created_at,d.reversed_by,d.reversal_reason,d.receipt_photo IS NOT NULL has_photo " +
                "FROM finance_documents d JOIN finance_accounts a ON a.id=d.account_id LEFT JOIN finance_accounts t ON t.id=d.to_account_id LEFT JOIN expense_types e ON e.id=d.expense_type_id" + where + " ORDER BY d.doc_date DESC,d.id DESC LIMIT 50 OFFSET ?", list.toArray());
        return Map.of("items", items, "total", jdbc.queryForObject("SELECT COUNT(*) FROM finance_documents d" + where, Long.class, args.toArray()), "page", p, "page_size", 50);
    }

    public Map<String,Object> photo(long id) {
        var rows = jdbc.queryForList("SELECT receipt_photo FROM finance_documents WHERE id=?", id);
        if (rows.isEmpty() || !(rows.get(0).get("receipt_photo") instanceof byte[] b)) throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        return Map.of("photo_data", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(b));
    }

    /** Daily cash count: the counted amount next to what the system expects for that day. */
    @Transactional
    public Map<String,Object> count(Map<String,Object> body) {
        long account = account(body.get("account_id"));
        LocalDate date = date(body.get("count_date"));
        if (date == null) date = today();
        PeriodClose.guard(date);
        BigDecimal counted = money(body.get("counted_amount"), true), system = balance(account, date);
        jdbc.update("INSERT INTO cash_counts(account_id,count_date,counted_amount,system_amount,note,created_by,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP())",
                account, date, counted, system, text(body.get("note"), 500, false), WarehouseService.actor());
        return Map.of("system_amount", system, "counted_amount", counted, "difference", counted.subtract(system));
    }

    public List<Map<String,Object>> counts() {
        return jdbc.queryForList("SELECT c.id,a.name account_name,DATE_FORMAT(c.count_date,'%Y-%m-%d') count_date,c.counted_amount,c.system_amount,c.counted_amount-c.system_amount difference,c.note,c.created_by FROM cash_counts c JOIN finance_accounts a ON a.id=c.account_id ORDER BY c.count_date DESC,c.id DESC LIMIT 60");
    }

    /** Opening + income − supplier payments − expenses ± transfers = closing, per money place (TZ v10 §9, T18). */
    @Transactional(readOnly = true)
    public Map<String,Object> movement(String fromText, String toText) {
        LocalDate from = date(fromText), to = date(toText);
        if (from == null) from = today().withDayOfMonth(1);
        if (to == null) to = today();
        if (to.isBefore(from)) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        var rows = new ArrayList<Map<String,Object>>();
        for (var a : jdbc.queryForList("SELECT id,name FROM finance_accounts ORDER BY id")) {
            long id = ((Number) a.get("id")).longValue();
            var row = new LinkedHashMap<String,Object>(a);
            // "start": balance before the period; "opening": opening balances set inside the period.
            row.put("start", balance(id, from.minusDays(1)));
            for (String kind : List.of("OPENING", "PAYME", "PAYME_REFUND", "INCOME", "SUPPLIER", "EXPENSE", "TRANSFER_IN", "TRANSFER_OUT"))
                row.put(kind.toLowerCase(), jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM (" + MOVEMENTS + ") x WHERE account_id=? AND kind=? AND d BETWEEN ? AND ?", BigDecimal.class, id, kind, from, to));
            row.put("closing", balance(id, to));
            rows.add(row);
        }
        return Map.of("from", from.toString(), "to", to.toString(), "accounts", rows);
    }

    /** Sales − cost − expenses = profit for one month (TZ v10 §9). Sales follow the dashboard rule: delivered, less the returned part. */
    @Transactional(readOnly = true)
    public Map<String,Object> monthly(String monthText) {
        YearMonth month;
        try { month = monthText == null || monthText.isBlank() ? YearMonth.now(TASHKENT) : YearMonth.parse(monthText); }
        catch (RuntimeException ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
        String inMonth = "o.status='DELIVERED' AND o.delivered_at>=CONVERT_TZ(?, '+05:00', '+00:00') AND o.delivered_at<CONVERT_TZ(?, '+05:00', '+00:00')";
        Object[] range = {month.atDay(1).atStartOfDay().toString().replace('T', ' '), month.plusMonths(1).atDay(1).atStartOfDay().toString().replace('T', ' ')};
        BigDecimal sales = jdbc.queryForObject("SELECT COALESCE(SUM(GREATEST(0,o.total_price-COALESCE((SELECT pr.refund_amount FROM delivery_partial_returns pr WHERE pr.order_id=o.id),0))),0) FROM orders o WHERE " + inMonth, BigDecimal.class, range);
        // Cost recorded at sale; if missing, the product's current cost price (counted as estimated); neither → unknown.
        // Goods the customer handed back at delivery (partial return) are not sold, so their cost is left out too.
        var cost = jdbc.queryForMap("SELECT COALESCE(SUM((i.quantity-COALESCE((SELECT SUM(ri.quantity) FROM delivery_partial_return_items ri WHERE ri.order_id=i.order_id AND ri.product_id=i.product_id),0))*COALESCE(i.unit_cost_snapshot,p.cost_price)),0) cost," +
                "COALESCE(SUM(CASE WHEN i.unit_cost_snapshot IS NULL AND p.cost_price IS NOT NULL THEN 1 ELSE 0 END),0) estimated," +
                "COALESCE(SUM(CASE WHEN i.unit_cost_snapshot IS NULL AND p.cost_price IS NULL THEN 1 ELSE 0 END),0) unknown " +
                "FROM order_items i JOIN orders o ON o.id=i.order_id LEFT JOIN products p ON p.id=i.product_id WHERE " + inMonth, range);
        var expenses = jdbc.queryForList("SELECT COALESCE(e.name,'—') type,SUM(d.amount) amount FROM finance_documents d LEFT JOIN expense_types e ON e.id=d.expense_type_id " +
                "WHERE d.kind='EXPENSE' AND d.status='POSTED' AND d.doc_date>=? AND d.doc_date<? GROUP BY e.name ORDER BY amount DESC", month.atDay(1), month.plusMonths(1).atDay(1));
        BigDecimal expenseTotal = expenses.stream().map(r -> (BigDecimal) r.get("amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costTotal = new BigDecimal(String.valueOf(cost.get("cost")));
        BigDecimal profit = sales.subtract(costTotal).subtract(expenseTotal);
        var result = new LinkedHashMap<String,Object>();
        result.put("month", month.toString()); result.put("sales", sales); result.put("cost", costTotal);
        result.put("cost_estimated_lines", ((Number) cost.get("estimated")).longValue()); result.put("cost_unknown_lines", ((Number) cost.get("unknown")).longValue());
        result.put("expenses", expenseTotal); result.put("expenses_by_type", expenses); result.put("profit", profit);
        result.put("margin_percent", sales.signum() == 0 ? null : profit.multiply(BigDecimal.valueOf(100)).divide(sales, 1, java.math.RoundingMode.HALF_UP));
        return result;
    }

    /** Money places for payment forms (names only, no balances). */
    public List<Map<String,Object>> places() { return jdbc.queryForList("SELECT id,name FROM finance_accounts ORDER BY id"); }

    private long account(Object value) {
        long id = WarehouseServiceAccess.positive(value);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM finance_accounts WHERE id=?", Integer.class, id) != 1) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        return id;
    }
    static BigDecimal money(Object value, boolean zeroAllowed) {
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value)).setScale(2, java.math.RoundingMode.UNNECESSARY);
            if ((zeroAllowed ? amount.signum() < 0 : amount.signum() <= 0) || amount.compareTo(new BigDecimal("9999999999999.99")) > 0) throw new IllegalArgumentException();
            return amount;
        } catch (RuntimeException ex) { throw new WarehouseException("FINANCE_INVALID_AMOUNT"); }
    }
    static LocalDate date(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return LocalDate.parse(String.valueOf(value).substring(0, 10)); }
        catch (RuntimeException ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
    }
    static String text(Object value, int max, boolean required) {
        String s = value == null ? "" : String.valueOf(value).trim();
        if (s.length() > max || (required && s.isEmpty())) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        return s.isEmpty() ? null : s;
    }
}
