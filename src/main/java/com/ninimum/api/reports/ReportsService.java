package com.ninimum.api.reports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.admin.access.StaffAccess;
import com.ninimum.api.warehouse.WarehouseException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * TZ v10 §11 "Hisobotlar": one endpoint per report, each with its filters. Every report returns
 * {columns:[{key,type,sum}], rows:[...]}; the web admin shows it and builds the Excel file from the same rows,
 * so Excel totals equal screen totals. Dates are Tashkent calendar days; times are stored in UTC.
 * Money columns (cost, sale value, purchase prices) are dropped for staff without money.view ("omborchi — narxsiz").
 */
@Service
public class ReportsService {
    static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    static final String UNIT_COST = "COALESCE((SELECT SUM(b.unit_cost*b.physical_quantity)/NULLIF(SUM(b.physical_quantity),0) FROM inventory_batches b WHERE b.product_id=p.id AND b.physical_quantity>0 AND b.unit_cost IS NOT NULL),p.cost_price)";
    static final String RESERVED = "COALESCE((SELECT SUM(i.quantity) FROM order_items i JOIN orders o ON o.id=i.order_id WHERE i.product_id=p.id AND o.payment_status='PAID' AND o.status IN ('PENDING','CONFIRMED','PREPARING','READY')),0)";
    /** When an order was cancelled (UTC): its own field, else the status history, else the order time. */
    static final String CANCELLED_AT = "COALESCE(o.cancelled_at,(SELECT MAX(h.changed_at) FROM order_status_history h WHERE h.order_id=o.id AND h.to_status='CANCELLED'),o.ordered_at)";
    static final String TASHKENT_DAY = "DATE(DATE_ADD(%s,INTERVAL 5 HOUR))";
    /** Report code → permission needed besides being signed in. Money reports 19/20 live on the Pul page. */
    static final Map<String,String> ACCESS = new LinkedHashMap<>();
    static {
        for (String c : List.of("stock","turnover","bins","unplaced","receipts","writeoffs","expiry","counts","dead-stock")) ACCESS.put(c, "warehouse.view");
        ACCESS.put("reorder", "warehouse.view|suppliers.view");
        for (String c : List.of("order-flow","problems","reasons","couriers")) ACCESS.put(c, "orders.view");
        ACCESS.put("sales", StaffAccess.MONEY);
        ACCESS.put("payment-calendar", "suppliers.view"); ACCESS.put("supplier-turnover", "suppliers.view");
        ACCESS.put("subscriptions", "subscriptions.view");
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public ReportsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Filters: from, to (yyyy-mm-dd), date, product_id, category_id, location_id, supplier_id, courier_id, days, group. */
    @Transactional(readOnly = true)
    public Map<String,Object> run(String code, Map<String,String> f) {
        String need = ACCESS.get(code);
        if (need == null) throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        if (Arrays.stream(need.split("\\|")).noneMatch(StaffAccess::can)) throw new AccessDeniedException("ADMIN_PERMISSION_DENIED");
        var q = new Query(f);
        Report r = switch (code) {
            case "stock" -> stock(q); case "turnover" -> turnover(q); case "bins" -> bins(q); case "unplaced" -> unplaced(q);
            case "receipts" -> receipts(q); case "writeoffs" -> writeoffs(q); case "expiry" -> expiry(q); case "counts" -> counts(q);
            case "order-flow" -> orderFlow(q); case "problems" -> problems(q); case "reasons" -> reasons(q); case "couriers" -> couriers(q);
            case "sales" -> sales(q); case "dead-stock" -> deadStock(q); case "reorder" -> reorder(q);
            case "payment-calendar" -> paymentCalendar(q); case "supplier-turnover" -> supplierTurnover(q); default -> subscriptions(q);
        };
        if (!StaffAccess.can(StaffAccess.MONEY)) r.hideMoney();
        var out = new LinkedHashMap<String,Object>();
        out.put("code", code); out.put("from", q.from.toString()); out.put("to", q.to.toString());
        out.put("columns", r.columns); out.put("rows", r.rows);
        return out;
    }

    /** Reports the signed-in user may open (the menu lists only these). */
    public List<String> available() {
        var out = new ArrayList<String>();
        ACCESS.forEach((code, need) -> { if (Arrays.stream(need.split("\\|")).anyMatch(StaffAccess::can)) out.add(code); });
        return out;
    }

    // 1. Stock now or at the end of a day: physical, reserved, free, cost value, sale value.
    Report stock(Query q) {
        var r = new Report().col("product_name","text").col("category_name","text").col("physical","number",true).col("reserved","number",true).col("free","number",true)
                .col("unit_cost","money").money("unit_cost").col("cost_sum","money",true).money("cost_sum").col("sale_price","money").money("sale_price").col("sale_sum","money",true).money("sale_sum");
        var args = new ArrayList<Object>();
        String free = "p.stock_quantity", reserved = RESERVED, physical = "IF(EXISTS(SELECT 1 FROM inventory_products ip WHERE ip.product_id=p.id),(SELECT COALESCE(SUM(b.physical_quantity),0) FROM inventory_batches b WHERE b.product_id=p.id),p.stock_quantity+" + RESERVED + ")";
        if (q.date != null) {
            // At a past day only the free stock is known (the stock ledger keeps it after every change).
            free = "COALESCE((SELECT m.stock_after FROM warehouse_stock_movements m WHERE m.product_id=p.id AND m.created_at<? ORDER BY m.id DESC LIMIT 1),0)";
            args.add(q.utcEnd(q.date)); reserved = "NULL"; physical = "NULL";
        }
        String where = q.productFilter("p", args);
        for (var row : jdbc.queryForList("SELECT p.id product_id,p.name product_name,c.name category_name," + free + " free," + reserved + " reserved," + physical + " physical," + UNIT_COST + " unit_cost,p.price sale_price " +
                "FROM products p LEFT JOIN categories c ON c.id=p.category_id WHERE 1=1" + where + " ORDER BY p.name", args.toArray())) {
            if (q.date != null) row.put("physical", row.get("free"));
            long physicalQty = n(row.get("physical"));
            if (physicalQty == 0 && n(row.get("free")) == 0) continue;
            row.put("cost_sum", mul(row.get("unit_cost"), physicalQty)); row.put("sale_sum", mul(row.get("sale_price"), physicalQty));
            r.rows.add(row);
        }
        return r;
    }

    // 2. Turnover: start + receipts + adjustments(+) + customer returns − sales − supplier returns − write-offs − adjustments(−) = end.
    Report turnover(Query q) {
        var r = new Report().col("product_name","text").col("start","number",true).col("receipt","number",true).col("adjust_plus","number",true).col("customer_return","number",true)
                .col("sale","number",true).col("supplier_return","number",true).col("writeoff","number",true).col("adjust_minus","number",true).col("end","number",true);
        var args = new ArrayList<Object>(List.of(q.utcStart(q.from), q.utcEnd(q.to), q.utcEnd(q.to)));
        String where = q.productFilter("p", args);
        String sql = "SELECT p.id product_id,p.name product_name," +
                "COALESCE(SUM(CASE WHEN m.kind='RECEIPT' AND m.reference NOT LIKE 'ORDER-RETURN-%' THEN m.quantity_change END),0) receipt," +
                "COALESCE(SUM(CASE WHEN (m.kind='RECEIPT' AND m.reference LIKE 'ORDER-RETURN-%') OR m.kind='PAYMENT_REFUND' THEN m.quantity_change END),0) customer_return," +
                "COALESCE(-SUM(CASE WHEN m.kind='SALE' THEN m.quantity_change END),0) sale," +
                "COALESCE(-SUM(CASE WHEN m.kind='RECEIPT_REVERSAL' THEN m.quantity_change END),0) supplier_return," +
                "COALESCE(-SUM(CASE WHEN m.kind='WRITEOFF' THEN m.quantity_change END),0) writeoff," +
                "COALESCE(SUM(CASE WHEN m.kind NOT IN ('RECEIPT','PAYMENT_REFUND','SALE','RECEIPT_REVERSAL','WRITEOFF') AND m.quantity_change>0 THEN m.quantity_change END),0) adjust_plus," +
                "COALESCE(-SUM(CASE WHEN m.kind NOT IN ('RECEIPT','PAYMENT_REFUND','SALE','RECEIPT_REVERSAL','WRITEOFF') AND m.quantity_change<0 THEN m.quantity_change END),0) adjust_minus," +
                "COALESCE(SUM(m.quantity_change),0) net," +
                "COALESCE((SELECT e.stock_after FROM warehouse_stock_movements e WHERE e.product_id=p.id AND e.created_at<? ORDER BY e.id DESC LIMIT 1),0) end_stock " +
                "FROM products p LEFT JOIN warehouse_stock_movements m ON m.product_id=p.id AND m.created_at>=? AND m.created_at<? WHERE 1=1" + where +
                " GROUP BY p.id,p.name ORDER BY p.name";
        // The end-of-period subquery comes first in the SELECT, so its parameter is first.
        args.add(0, args.remove(2));
        for (var row : jdbc.queryForList(sql, args.toArray())) {
            long end = n(row.remove("end_stock")), net = n(row.remove("net"));
            row.put("start", end - net); row.put("end", end);
            if (end == 0 && net == 0 && n(row.get("sale")) == 0 && n(row.get("receipt")) == 0) continue;
            r.rows.add(row);
        }
        return r;
    }

    // 3. Stock by bin: bin, product, quantity, batch, expiry, last movement.
    Report bins(Query q) {
        var r = new Report().col("location_code","text").col("product_name","text").col("quantity","number",true).col("reserved","number",true).col("batch_number","text").col("expiry_date","date").col("last_movement","datetime");
        var args = new ArrayList<Object>();
        String where = q.productFilter("p", args) + q.locationFilter("b", args);
        r.rows.addAll(jdbc.queryForList("SELECT l.code location_code,p.name product_name,b.physical_quantity quantity,b.reserved_quantity reserved,b.batch_number,DATE_FORMAT(b.expiry_date,'%Y-%m-%d') expiry_date," +
                "DATE_FORMAT((SELECT MAX(m.created_at) FROM inventory_movements m WHERE m.batch_id=b.id),'%Y-%m-%dT%H:%i:%sZ') last_movement " +
                "FROM inventory_batches b JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id WHERE b.physical_quantity>0" + where + " ORDER BY l.code,p.name,b.expiry_date", args.toArray()));
        return r;
    }

    // 4. Goods still in the receiving zone: since when, who received them.
    Report unplaced(Query q) {
        var r = new Report().col("location_code","text").col("product_name","text").col("quantity","number",true).col("received_at","datetime").col("received_by","text").col("days","number");
        var args = new ArrayList<Object>();
        String where = q.productFilter("p", args) + q.locationFilter("b", args);
        for (var row : jdbc.queryForList("SELECT l.code location_code,p.name product_name,b.physical_quantity quantity,DATE_FORMAT(b.created_at,'%Y-%m-%dT%H:%i:%sZ') received_at,b.created_by received_by,b.created_at raw " +
                "FROM inventory_batches b JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id WHERE b.physical_quantity>0 AND l.type='RECEIVING'" + where + " ORDER BY b.created_at", args.toArray())) {
            var at = (java.time.LocalDateTime) toDateTime(row.remove("raw"));
            row.put("days", at == null ? null : ChronoUnit.DAYS.between(at.atOffset(ZoneOffset.UTC).atZoneSameInstant(TASHKENT).toLocalDate(), today()));
            r.rows.add(row);
        }
        return r;
    }

    // 5. Receipts and mismatches: invoice quantity, accepted (fit), unfit, difference.
    Report receipts(Query q) {
        var r = new Report().col("receipt","text").col("created_at","datetime").col("status","status").col("supplier_name","text").col("invoice_number","text").col("product_name","text")
                .col("invoice_quantity","number",true).col("fit_quantity","number",true).col("unfit_quantity","number",true).col("difference","number",true).col("unfit_condition","status")
                .col("unit_price","money").money("unit_price").col("amount","money",true).money("amount");
        var args = new ArrayList<Object>(List.of(q.utcStart(q.from), q.utcEnd(q.to)));
        String where = q.productFilter("p", args);
        if (q.supplier != null) { where += " AND si.supplier_id=?"; args.add(q.supplier); }
        for (var row : jdbc.queryForList("SELECT CONCAT('KR-',r.id) receipt,DATE_FORMAT(r.created_at,'%Y-%m-%dT%H:%i:%sZ') created_at,r.status,COALESCE(s.name,r.supplier_name) supplier_name,r.invoice_number,i.product_name," +
                "i.quantity invoice_quantity,c.counted_quantity fit_quantity,c.unfit_quantity,c.unfit_condition,i.unit_price " +
                "FROM warehouse_receipts r JOIN warehouse_receipt_items i ON i.receipt_id=r.id JOIN products p ON p.id=i.product_id LEFT JOIN warehouse_receipt_counts c ON c.receipt_id=r.id AND c.product_id=i.product_id " +
                "LEFT JOIN supplier_invoices si ON si.receipt_id=r.id LEFT JOIN suppliers s ON s.id=si.supplier_id WHERE r.created_at>=? AND r.created_at<?" + where + " ORDER BY r.id DESC,i.id", args.toArray())) {
            long invoice = n(row.get("invoice_quantity"));
            // Receipts posted before blind counts were stored (KR-1…4) took the invoice quantity as it was.
            if (row.get("fit_quantity") == null && !"DRAFT".equals(row.get("status"))) row.put("fit_quantity", invoice);
            // Before posting nothing is counted yet: no difference to show.
            if (row.get("fit_quantity") != null) row.put("difference", n(row.get("fit_quantity")) - invoice);
            row.put("amount", mul(row.get("unit_price"), invoice));
            r.rows.add(row);
        }
        return r;
    }

    // 6. Write-offs: product, reason, quantity, cost, who, share of the product's sales in the period.
    Report writeoffs(Query q) {
        var r = new Report().col("document","text").col("approved_at","datetime").col("product_name","text").col("location_code","text").col("reason_code","status").col("reason","text")
                .col("quantity","number",true).col("cost","money",true).money("cost").col("created_by","text").col("approved_by","text").col("sales_share","percent");
        var args = new ArrayList<Object>(List.of(q.utcStart(q.from), q.utcEnd(q.to)));
        String where = q.productFilter("p", args) + q.locationFilter("d", args);
        var sold = soldInPeriod(q);
        for (var row : jdbc.queryForList("SELECT CONCAT('INV-',d.id) document,DATE_FORMAT(d.approved_at,'%Y-%m-%dT%H:%i:%sZ') approved_at,d.product_id,p.name product_name,l.code location_code,d.reason,d.payload,d.cost_total cost,d.created_by,d.approved_by " +
                "FROM inventory_documents d JOIN products p ON p.id=d.product_id LEFT JOIN warehouse_locations l ON l.id=d.location_id WHERE d.kind='WRITEOFF' AND d.status='POSTED' AND d.approved_at>=? AND d.approved_at<?" + where + " ORDER BY d.approved_at DESC", args.toArray())) {
            var payload = decode(row.remove("payload"));
            long qty = n(payload.get("quantity"));
            row.put("quantity", qty); row.put("reason_code", payload.get("reason_code"));
            long product = n(row.remove("product_id")), sales = sold.getOrDefault(product, 0L);
            row.put("sales_share", sales == 0 ? null : pct(qty, sales));
            r.rows.add(row);
        }
        return r;
    }

    // 7. Expiry: expired and soon-to-expire batches with their bin.
    Report expiry(Query q) {
        var r = new Report().col("product_name","text").col("location_code","text").col("batch_number","text").col("expiry_date","date").col("days_left","number").col("quantity","number",true)
                .col("cost_sum","money",true).money("cost_sum");
        var args = new ArrayList<Object>(List.of(today().plusDays(q.days(30))));
        String where = q.productFilter("p", args) + q.locationFilter("b", args);
        for (var row : jdbc.queryForList("SELECT p.name product_name,l.code location_code,b.batch_number,DATE_FORMAT(b.expiry_date,'%Y-%m-%d') expiry_date,b.physical_quantity quantity,b.unit_cost " +
                "FROM inventory_batches b JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id WHERE b.physical_quantity>0 AND b.expiry_date IS NOT NULL AND b.expiry_date<=?" + where + " ORDER BY b.expiry_date,p.name", args.toArray())) {
            row.put("days_left", ChronoUnit.DAYS.between(today(), LocalDate.parse(String.valueOf(row.get("expiry_date")))));
            row.put("cost_sum", mul(row.remove("unit_cost"), n(row.get("quantity"))));
            r.rows.add(row);
        }
        return r;
    }

    // 8. Count results: system, counted, difference in pieces and money, reason.
    Report counts(Query q) {
        var r = new Report().col("document","text").col("approved_at","datetime").col("location_code","text").col("product_name","text").col("batch_number","text")
                .col("expected","number",true).col("counted","number",true).col("difference","number",true).col("difference_sum","money",true).money("difference_sum").col("reason","text").col("created_by","text");
        var args = new ArrayList<Object>(List.of(q.utcStart(q.from), q.utcEnd(q.to)));
        String where = q.locationFilter("d", args);
        for (var doc : jdbc.queryForList("SELECT d.id,DATE_FORMAT(d.approved_at,'%Y-%m-%dT%H:%i:%sZ') approved_at,l.code location_code,d.payload,d.created_by FROM inventory_documents d LEFT JOIN warehouse_locations l ON l.id=d.location_id " +
                "WHERE d.kind='COUNT' AND d.status='POSTED' AND d.approved_at>=? AND d.approved_at<?" + where + " ORDER BY d.approved_at DESC", args.toArray())) {
            var payload = decode(doc.get("payload"));
            if (!(payload.get("lines") instanceof List<?> lines)) continue;
            for (Object o : lines) {
                if (!(o instanceof Map<?,?> line)) continue;
                long product = n(line.get("product_id"));
                if (q.product != null && product != q.product) continue;
                long expected = n(line.get("expected_quantity")), counted = n(line.get("counted_quantity"));
                var cost = jdbc.queryForList("SELECT unit_cost FROM inventory_batches WHERE id=?", line.get("batch_id"));
                var row = new LinkedHashMap<String,Object>();
                row.put("document", "INV-" + doc.get("id")); row.put("approved_at", doc.get("approved_at")); row.put("location_code", doc.get("location_code"));
                row.put("product_name", line.get("product_name")); row.put("batch_number", line.get("batch_number"));
                row.put("expected", expected); row.put("counted", counted); row.put("difference", counted - expected);
                row.put("difference_sum", cost.isEmpty() ? null : mul(cost.get(0).get("unit_cost"), counted - expected));
                row.put("reason", counted == expected ? null : payload.get("variance_reason")); row.put("created_by", doc.get("created_by"));
                r.rows.add(row);
            }
        }
        return r;
    }

    // 9. Order flow: average time in each status (or per packer), and orders whose timer ran over.
    Report orderFlow(Query q) {
        if ("packer".equals(q.group)) {
            var r = new Report().col("worker","text").col("orders","number",true).col("avg_minutes","number").col("max_minutes","number");
            r.rows.addAll(jdbc.queryForList("SELECT COALESCE(w.worker_code,'—') worker,COUNT(*) orders,ROUND(AVG(TIMESTAMPDIFF(SECOND,w.started_at,w.ready_at))/60,1) avg_minutes,ROUND(MAX(TIMESTAMPDIFF(SECOND,w.started_at,w.ready_at))/60,1) max_minutes " +
                    "FROM warehouse_preparations w WHERE w.ready_at IS NOT NULL AND w.started_at IS NOT NULL AND w.ready_at>=? AND w.ready_at<? GROUP BY w.worker_code ORDER BY orders DESC", q.utcStart(q.from), q.utcEnd(q.to)));
            return r;
        }
        var r = new Report().col("status","status").col("orders","number",true).col("avg_minutes","number").col("max_minutes","number").col("overdue","number",true);
        var spent = new LinkedHashMap<String,List<Double>>();
        long lastOrder = -1; String lastStatus = null; LocalDateTime lastAt = null;
        for (var h : jdbc.queryForList("SELECT h.order_id,h.to_status,h.changed_at FROM order_status_history h JOIN orders o ON o.id=h.order_id WHERE o.ordered_at>=? AND o.ordered_at<? ORDER BY h.order_id,h.changed_at,h.id", q.utcStart(q.from), q.utcEnd(q.to))) {
            long order = n(h.get("order_id")); var at = toDateTime(h.get("changed_at"));
            if (order == lastOrder && lastStatus != null && lastAt != null && at != null) spent.computeIfAbsent(lastStatus, k -> new ArrayList<>()).add(Duration.between(lastAt, at).getSeconds() / 60.0);
            lastOrder = order; lastStatus = String.valueOf(h.get("to_status")); lastAt = at;
        }
        var overdue = new HashMap<String,Long>();
        for (var a : jdbc.queryForList("SELECT order_status,COUNT(DISTINCT entity_id) n FROM order_problem_alerts WHERE entity_type='ORDER' AND opened_at>=? AND opened_at<? GROUP BY order_status", q.utcStart(q.from), q.utcEnd(q.to)))
            overdue.put(String.valueOf(a.get("order_status")), n(a.get("n")));
        for (String s : List.of("PENDING","CONFIRMED","PREPARING","READY","ON_THE_WAY","RETURNING")) {
            var list = spent.getOrDefault(s, List.of());
            var row = new LinkedHashMap<String,Object>();
            row.put("status", s); row.put("orders", (long) list.size());
            row.put("avg_minutes", list.isEmpty() ? null : round1(list.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
            row.put("max_minutes", list.isEmpty() ? null : round1(list.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
            row.put("overdue", overdue.getOrDefault(s, 0L));
            r.rows.add(row);
        }
        return r;
    }

    // 10. Problem orders and money control: paid but not delivered, paid but cancelled, refunds not made.
    Report problems(Query q) {
        var r = new Report().col("problem","status").col("order_number","text").col("status","status").col("amount","money",true).col("ordered_at","datetime").col("days","number");
        String cols = "o.order_number,o.status,DATE_FORMAT(o.ordered_at,'%Y-%m-%dT%H:%i:%sZ') ordered_at,DATEDIFF(UTC_TIMESTAMP(),o.ordered_at) days";
        add(r, "PAID_NOT_DELIVERED", jdbc.queryForList("SELECT o.total_price amount," + cols + " FROM orders o WHERE o.payment_status='PAID' AND o.status NOT IN ('DELIVERED','CANCELLED','RETURNED') ORDER BY o.ordered_at"));
        add(r, "PAID_CANCELLED", jdbc.queryForList("SELECT o.total_price amount," + cols + " FROM orders o WHERE o.payment_status='PAID' AND o.status='CANCELLED' ORDER BY o.ordered_at"));
        add(r, "REFUND_PENDING", jdbc.queryForList("SELECT pr.refund_amount amount," + cols + " FROM delivery_partial_returns pr JOIN orders o ON o.id=pr.order_id WHERE pr.refund_status='PENDING' AND pr.refund_amount>0 ORDER BY o.ordered_at"));
        return r;
    }

    // 11. Cancellation and return reasons: reason, count, amount, share of all orders in the period.
    Report reasons(Query q) {
        var r = new Report().col("type","status").col("reason","text").col("orders","number",true).col("amount","money",true).col("share","percent");
        Object a = q.utcStart(q.from), b = q.utcEnd(q.to);
        long all = n(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE ordered_at>=? AND ordered_at<?", Long.class, a, b));
        add(r, "CANCELLED", jdbc.queryForList("SELECT COALESCE(NULLIF(TRIM(cancel_reason),''),'—') reason,COUNT(*) orders,SUM(total_price) amount FROM orders o WHERE status='CANCELLED' AND " + CANCELLED_AT + ">=? AND " + CANCELLED_AT + "<? GROUP BY 1 ORDER BY orders DESC", a, b));
        add(r, "DELIVERY_FAILED", jdbc.queryForList("SELECT COALESCE(NULLIF(TRIM(j.note),''),'—') reason,COUNT(*) orders,SUM(o.total_price) amount FROM delivery_jobs j JOIN orders o ON o.id=j.order_id WHERE j.status='FAILED' AND j.updated_at>=? AND j.updated_at<? GROUP BY 1 ORDER BY orders DESC", a, b));
        add(r, "PARTIAL_RETURN", jdbc.queryForList("SELECT COALESCE(NULLIF(TRIM(reason),''),'—') reason,COUNT(*) orders,SUM(refund_amount) amount FROM delivery_partial_returns WHERE created_at>=? AND created_at<? GROUP BY 1 ORDER BY orders DESC", a, b));
        for (var row : r.rows) row.put("share", all == 0 ? null : pct(n(row.get("orders")), all));
        return r;
    }

    // 12. Couriers: delivered, failed, average delivery time, offers not accepted.
    Report couriers(Query q) {
        var r = new Report().col("courier","text").col("delivered","number",true).col("failed","number",true).col("avg_minutes","number").col("declined","number",true).col("expired","number",true);
        Object a = q.utcStart(q.from), b = q.utcEnd(q.to);
        var args = new ArrayList<Object>(List.of(a, b, a, b, a, b, a, b, a, b));
        String where = "";
        if (q.courier != null) { where = " WHERE w.id=?"; args.add(q.courier); }
        for (var row : jdbc.queryForList("SELECT w.full_name courier," +
                "(SELECT COUNT(*) FROM delivery_jobs j WHERE j.delivery_worker_id=w.id AND j.status='DELIVERED' AND j.delivered_at>=? AND j.delivered_at<?) delivered," +
                "(SELECT COUNT(*) FROM delivery_jobs j WHERE j.delivery_worker_id=w.id AND j.status='FAILED' AND j.updated_at>=? AND j.updated_at<?) failed," +
                "(SELECT ROUND(AVG(TIMESTAMPDIFF(SECOND,COALESCE(j.on_the_way_at,j.accepted_at),j.delivered_at))/60,1) FROM delivery_jobs j WHERE j.delivery_worker_id=w.id AND j.status='DELIVERED' AND j.delivered_at>=? AND j.delivered_at<?) avg_minutes," +
                "(SELECT COUNT(*) FROM courier_dispatch_history h WHERE h.worker_id=w.id AND h.event='DECLINED' AND h.occurred_at>=? AND h.occurred_at<?) declined," +
                "(SELECT COUNT(*) FROM courier_dispatch_history h WHERE h.worker_id=w.id AND h.event='EXPIRED' AND h.occurred_at>=? AND h.occurred_at<?) expired " +
                "FROM delivery_app_workers w" + where + " ORDER BY w.full_name", args.toArray())) {
            if (q.courier == null && n(row.get("delivered")) + n(row.get("failed")) + n(row.get("declined")) + n(row.get("expired")) == 0) continue;
            r.rows.add(row);
        }
        return r;
    }

    // 13. Sales and gross profit by product, category or day (delivered orders; partial returns taken off).
    Report sales(Query q) {
        String group = "category".equals(q.group) || "day".equals(q.group) ? q.group : "product";
        String key = switch (group) { case "category" -> "COALESCE(c.name,'—')"; case "day" -> String.format(TASHKENT_DAY, "o.delivered_at"); default -> "i.product_name"; };
        var r = new Report().col(group.equals("day") ? "day" : group.equals("category") ? "category_name" : "product_name", group.equals("day") ? "date" : "text")
                .col("quantity","number",true).col("sales","money",true).col("cost","money",true).col("profit","money",true).col("margin","percent").col("cost_missing","number",true);
        var args = new ArrayList<Object>(List.of(q.utcStart(q.from), q.utcEnd(q.to)));
        String where = q.productFilter("p", args);
        String returned = "COALESCE((SELECT SUM(ri.quantity) FROM delivery_partial_return_items ri WHERE ri.order_id=i.order_id AND ri.product_id=i.product_id),0)";
        String qty = "(i.quantity-" + returned + ")";
        for (var row : jdbc.queryForList("SELECT " + key + " k,SUM(" + qty + ") quantity,SUM(i.unit_price*" + qty + ") sales," +
                "SUM(" + qty + "*COALESCE(i.unit_cost_snapshot,p.cost_price)) cost,SUM(CASE WHEN COALESCE(i.unit_cost_snapshot,p.cost_price) IS NULL AND " + qty + ">0 THEN 1 ELSE 0 END) cost_missing " +
                "FROM order_items i JOIN orders o ON o.id=i.order_id JOIN products p ON p.id=i.product_id LEFT JOIN categories c ON c.id=p.category_id " +
                "WHERE o.status='DELIVERED' AND o.delivered_at>=? AND o.delivered_at<?" + where + " GROUP BY k ORDER BY sales DESC", args.toArray())) {
            var out = new LinkedHashMap<String,Object>();
            out.put(r.columns.get(0).get("key").toString(), group.equals("day") ? String.valueOf(row.get("k")) : row.get("k"));
            BigDecimal sales = dec(row.get("sales")), cost = dec(row.get("cost"));
            out.put("quantity", n(row.get("quantity"))); out.put("sales", sales); out.put("cost", cost);
            out.put("profit", sales.subtract(cost)); out.put("margin", sales.signum() == 0 ? null : sales.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(sales, 1, RoundingMode.HALF_UP));
            out.put("cost_missing", n(row.get("cost_missing")));
            r.rows.add(out);
        }
        return r;
    }

    // 14. Dead stock: products with stock and no sale for N days, and the money tied up in them.
    Report deadStock(Query q) {
        int days = q.days(30);
        var r = new Report().col("product_name","text").col("category_name","text").col("stock","number",true).col("last_sale","datetime").col("days_without_sale","number")
                .col("frozen_money","money",true).money("frozen_money");
        var args = new ArrayList<Object>();
        String where = q.productFilter("p", args);
        for (var row : jdbc.queryForList("SELECT p.name product_name,c.name category_name,p.stock_quantity stock," + UNIT_COST + " unit_cost," +
                "(SELECT MAX(m.created_at) FROM warehouse_stock_movements m WHERE m.product_id=p.id AND m.kind='SALE') last_sale_raw,p.created_at " +
                "FROM products p LEFT JOIN categories c ON c.id=p.category_id WHERE p.stock_quantity>0" + where + " ORDER BY p.name", args.toArray())) {
            var last = toDateTime(row.remove("last_sale_raw")); var created = toDateTime(row.remove("created_at"));
            var since = last != null ? last : created;
            long idle = since == null ? days : ChronoUnit.DAYS.between(since.atOffset(ZoneOffset.UTC).atZoneSameInstant(TASHKENT).toLocalDate(), today());
            if (idle < days) continue;
            row.put("last_sale", last == null ? null : last.toString() + "Z"); row.put("days_without_sale", idle);
            row.put("frozen_money", mul(row.remove("unit_cost"), n(row.get("stock"))));
            r.rows.add(row);
        }
        return r;
    }

    // 15. Suggested stock: daily average sale (calendar days), days the free stock lasts, 7/14/30-day suggestion.
    Report reorder(Query q) {
        var r = new Report().col("product_name","text").col("sold","number",true).col("daily_avg","number").col("free","number",true).col("days_cover","number")
                .col("need_7","number",true).col("need_14","number",true).col("need_30","number",true);
        long calendarDays = ChronoUnit.DAYS.between(q.from, q.to) + 1;
        var sold = soldInPeriod(q);
        var args = new ArrayList<Object>();
        String where = q.productFilter("p", args);
        for (var row : jdbc.queryForList("SELECT p.id,p.name product_name,p.stock_quantity free FROM products p WHERE p.is_active=1" + where + " ORDER BY p.name", args.toArray())) {
            long product = n(row.remove("id")), qty = sold.getOrDefault(product, 0L), free = n(row.get("free"));
            if (qty == 0 && free == 0) continue;
            double avg = (double) qty / calendarDays;
            row.put("sold", qty); row.put("daily_avg", round1(avg));
            row.put("days_cover", avg == 0 ? null : round1(free / avg));
            for (int n : new int[]{7, 14, 30}) row.put("need_" + n, Math.max(0, (long) Math.ceil(avg * n) - free));
            r.rows.add(row);
        }
        return r;
    }

    // 16. Supplier payment calendar: amount, paid, outstanding, due date, days left (negative = overdue).
    Report paymentCalendar(Query q) {
        var r = new Report().col("supplier_name","text").col("receipt","text").col("invoice_number","text").col("invoice_date","date").col("amount","money",true).col("paid","money",true)
                .col("outstanding","money",true).col("due_date","date").col("days_left","number");
        var args = new ArrayList<Object>();
        String where = "";
        if (q.supplier != null) { where = " AND i.supplier_id=?"; args.add(q.supplier); }
        for (var row : jdbc.queryForList("SELECT s.name supplier_name,CONCAT('KR-',i.receipt_id) receipt,i.invoice_number,DATE_FORMAT(i.invoice_date,'%Y-%m-%d') invoice_date,i.invoice_amount amount," +
                "COALESCE((SELECT SUM(p.amount) FROM supplier_payment_records p WHERE p.receipt_id=i.receipt_id AND p.status='RECORDED'),0) paid,DATE_FORMAT(i.due_date,'%Y-%m-%d') due_date " +
                "FROM supplier_invoices i JOIN suppliers s ON s.id=i.supplier_id JOIN warehouse_receipts r ON r.id=i.receipt_id WHERE r.status='POSTED'" + where + " ORDER BY i.due_date", args.toArray())) {
            BigDecimal outstanding = dec(row.get("amount")).subtract(dec(row.get("paid")));
            if (outstanding.signum() <= 0) continue;
            row.put("outstanding", outstanding);
            row.put("days_left", row.get("due_date") == null ? null : ChronoUnit.DAYS.between(today(), LocalDate.parse(String.valueOf(row.get("due_date")))));
            r.rows.add(row);
        }
        return r;
    }

    // 17. Supplier turnover: start + receipts − payments − returns (reversed receipts) = end.
    Report supplierTurnover(Query q) {
        var r = new Report().col("supplier_name","text").col("start","money",true).col("receipts","money",true).col("payments","money",true).col("returns","money",true).col("end","money",true);
        Object a = q.utcStart(q.from), b = q.utcEnd(q.to);
        var args = new ArrayList<Object>(List.of(a, a, q.from, a, b, a, b, q.from, q.to));
        String where = "";
        if (q.supplier != null) { where = " WHERE s.id=?"; args.add(q.supplier); }
        String posted = "SELECT COALESCE(SUM(i.invoice_amount),0) FROM supplier_invoices i JOIN warehouse_receipts r ON r.id=i.receipt_id WHERE i.supplier_id=s.id AND r.posted_at IS NOT NULL";
        for (var row : jdbc.queryForList("SELECT s.name supplier_name," +
                "(" + posted + " AND r.posted_at<?) - (" + posted + " AND r.status='REVERSED' AND r.reversed_at<?) - (SELECT COALESCE(SUM(p.amount),0) FROM supplier_payment_records p WHERE p.supplier_id=s.id AND p.status='RECORDED' AND p.paid_date<?) start," +
                "(" + posted + " AND r.posted_at>=? AND r.posted_at<?) receipts," +
                "(" + posted + " AND r.status='REVERSED' AND r.reversed_at>=? AND r.reversed_at<?) `returns`," +
                "(SELECT COALESCE(SUM(p.amount),0) FROM supplier_payment_records p WHERE p.supplier_id=s.id AND p.status='RECORDED' AND p.paid_date>=? AND p.paid_date<=?) payments " +
                "FROM suppliers s" + where + " ORDER BY s.name", args.toArray())) {
            BigDecimal start = dec(row.get("start")), end = start.add(dec(row.get("receipts"))).subtract(dec(row.get("payments"))).subtract(dec(row.get("returns")));
            row.put("end", end);
            if (start.signum() == 0 && end.signum() == 0 && dec(row.get("receipts")).signum() == 0 && dec(row.get("payments")).signum() == 0) continue;
            r.rows.add(row);
        }
        return r;
    }

    // 18. Subscriptions by plan: active now, new, ended, renewed, subscription income in the period.
    Report subscriptions(Query q) {
        var r = new Report().col("plan","text").col("active","number",true).col("new","number",true).col("ended","number",true).col("renewed","number",true).col("income","money",true);
        Object a = q.utcStart(q.from), b = q.utcEnd(q.to);
        long from = q.from.atStartOfDay(TASHKENT).toInstant().toEpochMilli(), to = q.to.plusDays(1).atStartOfDay(TASHKENT).toInstant().toEpochMilli();
        r.rows.addAll(jdbc.queryForList("SELECT t.name plan," +
                "(SELECT COUNT(*) FROM user_tariffs u WHERE u.tariff_plan_id=t.id AND u.status='ACTIVE' AND (u.end_date IS NULL OR u.end_date>UTC_TIMESTAMP())) active," +
                "(SELECT COUNT(*) FROM user_tariffs u WHERE u.tariff_plan_id=t.id AND u.activated_at>=? AND u.activated_at<?) `new`," +
                "(SELECT COUNT(*) FROM user_tariffs u WHERE u.tariff_plan_id=t.id AND u.end_date>=? AND u.end_date<? AND u.end_date<=UTC_TIMESTAMP()) ended," +
                "(SELECT COUNT(*) FROM user_tariffs u WHERE u.tariff_plan_id=t.id AND u.activated_at>=? AND u.activated_at<? AND EXISTS(SELECT 1 FROM user_tariffs e WHERE e.user_id=u.user_id AND e.id<>u.id AND e.activated_at<u.activated_at)) renewed," +
                "(SELECT COALESCE(SUM(p.amount),0) FROM payments p JOIN user_tariffs u ON u.id=p.user_tariff_id WHERE u.tariff_plan_id=t.id AND p.status='PAID' AND p.payme_perform_time>=? AND p.payme_perform_time<?) income " +
                "FROM tariff_plans t ORDER BY t.name", a, b, a, b, a, b, from, to));
        return r;
    }

    // ---- helpers
    /** Sold quantity per product in the period (paid orders, not cancelled), by order time. */
    private Map<Long,Long> soldInPeriod(Query q) {
        var out = new HashMap<Long,Long>();
        for (var row : jdbc.queryForList("SELECT i.product_id,SUM(i.quantity) qty FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.payment_status='PAID' AND o.status<>'CANCELLED' AND o.ordered_at>=? AND o.ordered_at<? GROUP BY i.product_id", q.utcStart(q.from), q.utcEnd(q.to)))
            out.put(n(row.get("product_id")), n(row.get("qty")));
        return out;
    }
    private static void add(Report r, String type, List<Map<String,Object>> rows) {
        String key = r.columns.get(0).get("key").toString();
        for (var row : rows) { var out = new LinkedHashMap<String,Object>(); out.put(key, type); out.putAll(row); r.rows.add(out); }
    }
    private Map<String,Object> decode(Object value) {
        try { return json.readValue(String.valueOf(value), new TypeReference<Map<String,Object>>() {}); } catch (Exception ex) { return Map.of(); }
    }
    static LocalDate today() { return LocalDate.now(TASHKENT); }
    static long n(Object v) { return v == null ? 0 : v instanceof Number x ? x.longValue() : new BigDecimal(String.valueOf(v)).longValue(); }
    static BigDecimal dec(Object v) { return v == null ? BigDecimal.ZERO : v instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(v)); }
    static BigDecimal mul(Object price, long qty) { return price == null ? null : dec(price).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP); }
    static BigDecimal pct(long part, long whole) { return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP); }
    static double round1(double v) { return Math.round(v * 10) / 10.0; }
    static LocalDateTime toDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime t) return t;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(v).replace(' ', 'T').replaceAll("\\.\\d+$", ""));
    }

    /** Column list plus rows; money columns can be removed for staff without money.view. */
    static class Report {
        final List<Map<String,Object>> columns = new ArrayList<>();
        final List<Map<String,Object>> rows = new ArrayList<>();
        final Set<String> money = new HashSet<>();
        Report col(String key, String type) { return col(key, type, false); }
        Report col(String key, String type, boolean sum) { columns.add(new LinkedHashMap<>(Map.of("key", key, "type", type, "sum", sum))); return this; }
        Report money(String key) { money.add(key); return this; }
        void hideMoney() {
            columns.removeIf(c -> money.contains(String.valueOf(c.get("key"))));
            for (var row : rows) row.keySet().removeAll(money);
        }
    }

    /** Parsed filters. Without dates the period is the current month up to today. */
    static class Query {
        final LocalDate from, to, date; final Long product, category, location, supplier, courier; final String group; final Integer daysParam;
        Query(Map<String,String> f) {
            LocalDate t = day(f.get("to")), fr = day(f.get("from"));
            to = t == null ? today() : t; from = fr == null ? to.withDayOfMonth(1) : fr;
            if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 3660) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            date = day(f.get("date")); product = id(f.get("product_id")); category = id(f.get("category_id")); location = id(f.get("location_id"));
            supplier = id(f.get("supplier_id")); courier = id(f.get("courier_id")); group = f.get("group");
            Long d = id(f.get("days")); daysParam = d == null ? null : (int) Math.min(d, 3650);
        }
        int days(int fallback) { return daysParam == null ? fallback : daysParam; }
        LocalDateTime utcStart(LocalDate d) { return d.atStartOfDay(TASHKENT).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
        LocalDateTime utcEnd(LocalDate d) { return utcStart(d.plusDays(1)); }
        String productFilter(String p, List<Object> args) {
            String w = "";
            if (product != null) { w += " AND " + p + ".id=?"; args.add(product); }
            if (category != null) { w += " AND (" + p + ".category_id=? OR " + p + ".category_id IN (SELECT id FROM categories WHERE parent_id=?))"; args.add(category); args.add(category); }
            return w;
        }
        String locationFilter(String t, List<Object> args) {
            if (location == null) return "";
            args.add(location); return " AND " + t + ".location_id=?";
        }
        static LocalDate day(String v) {
            if (v == null || v.isBlank()) return null;
            try { return LocalDate.parse(v.trim()); } catch (RuntimeException ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
        }
        static Long id(String v) {
            if (v == null || v.isBlank()) return null;
            try { long n = Long.parseLong(v.trim()); if (n <= 0) throw new NumberFormatException(); return n; } catch (NumberFormatException ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
        }
    }
}
