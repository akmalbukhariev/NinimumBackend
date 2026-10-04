package com.ninimum.api.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/** Stage one tracks sellable stock. Shelf-level physical/reserved balances are a later migration. */
@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final JdbcTemplate jdbc;
    @Value("${warehouse.enabled:false}") private boolean enabled;

    public boolean isEnabled() { return enabled; }

    @PostConstruct public void verifySchema() {
        if (enabled && !Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT version FROM warehouse_schema WHERE id=1", Integer.class))) {
            throw new IllegalStateException("Run warehouse-step1.sql before enabling WAREHOUSE_ENABLED");
        }
    }

    private void requireEnabled() { if (!enabled) throw new WarehouseException("WAREHOUSE_NOT_ENABLED"); }
    private static boolean active(Object value) { return Boolean.TRUE.equals(value) || (value instanceof Number && ((Number)value).intValue()==1); }
    public static String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "SYSTEM" : auth.getName();
    }
    static String text(Object value, int max, boolean required) {
        String s = value == null ? "" : String.valueOf(value).trim();
        if (s.length() > max || (required && s.isEmpty())) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        return s;
    }
    static long positive(Object value) {
        try {
            long n = new BigDecimal(String.valueOf(value)).longValueExact();
            if (n <= 0 || n > Integer.MAX_VALUE) throw new NumberFormatException();
            return n;
        } catch (Exception ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
    }
    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private Map<String,Object> one(String sql, Object... values) {
        List<Map<String,Object>> rows = jdbc.queryForList(sql, values);
        if (rows.isEmpty()) throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        return rows.get(0);
    }
    private Map<String,Object> page(String from, String select, String order, int page, int size, Object... args) {
        requireEnabled();
        int p = Math.max(1, Math.min(page, 1000000)), s = Math.max(1, Math.min(size, 100));
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args);
        List<Object> values = new ArrayList<>(Arrays.asList(args));
        values.add(s); values.add((p - 1) * s);
        return Map.of("items", jdbc.queryForList("SELECT " + select + " " + from + " " + order + " LIMIT ? OFFSET ?", values.toArray()),
                "total", total, "page", p, "page_size", s);
    }
    private static final String UTC = "'%Y-%m-%dT%H:%i:%sZ'";

    public List<Map<String,Object>> locations() {
        requireEnabled();
        return jdbc.queryForList("SELECT id,code,name,type,is_active FROM warehouse_locations ORDER BY code");
    }
    @Transactional public long createLocation(Map<String,Object> body) {
        requireEnabled();
        String code = text(body.get("code"), 40, true).toUpperCase(Locale.ROOT);
        String name = text(body.get("name"), 120, true);
        String type = text(body.get("type"), 20, true);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]*") || !Set.of("SHELF", "RECEIVING").contains(type))
            throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        return insert("INSERT INTO warehouse_locations(code,name,type,created_by,created_at) VALUES (?,?,?,?,UTC_TIMESTAMP())",
                code, name, type, actor());
    }

    public Map<String,Object> stock(String search, int page, int size) {
        String q = "%" + text(search, 120, false) + "%";
        return page("FROM products p WHERE (p.name LIKE ? OR p.barcode LIKE ? OR p.sku LIKE ?)",
                "p.id,p.name,p.sku,p.barcode,p.stock_quantity,p.is_active", "ORDER BY p.name,p.id", page, size, q,q,q);
    }
    public Map<String,Object> movements(Long productId, int page, int size) {
        String from = "FROM warehouse_stock_movements m LEFT JOIN products p ON p.id=m.product_id";
        if (productId != null) from += " WHERE m.product_id=?";
        return page(from, "m.id,m.product_id,COALESCE(p.name,m.product_name) AS product_name,m.kind,m.quantity_change," +
                "m.stock_after,m.reference,m.actor,m.note,DATE_FORMAT(m.created_at," + UTC + ") AS created_at",
                "ORDER BY m.id DESC", page, size, productId == null ? new Object[0] : new Object[]{productId});
    }
    public Map<String,Object> receipts(int page, int size) {
        return page("FROM warehouse_receipts r", "r.id,r.supplier_name,r.invoice_number,r.status,r.created_by,r.posted_by," +
                "r.reversed_by,r.reversal_reason,DATE_FORMAT(r.created_at," + UTC + ") AS created_at," +
                "DATE_FORMAT(r.posted_at," + UTC + ") AS posted_at", "ORDER BY r.id DESC", page,size);
    }
    public Map<String,Object> receipt(long id) {
        requireEnabled();
        Map<String,Object> result = new LinkedHashMap<>(one("SELECT r.*,DATE_FORMAT(r.created_at," + UTC + ") AS created_at," +
                "DATE_FORMAT(r.posted_at," + UTC + ") AS posted_at,DATE_FORMAT(r.reversed_at," + UTC + ") AS reversed_at " +
                "FROM warehouse_receipts r WHERE r.id=?",id));
        result.put("items",jdbc.queryForList("SELECT i.id,i.product_id,i.product_name,i.quantity,i.location_id,l.code AS location_code " +
                "FROM warehouse_receipt_items i JOIN warehouse_locations l ON l.id=i.location_id WHERE i.receipt_id=? ORDER BY i.id",id));
        return result;
    }

    @Transactional public long createReceipt(Map<String,Object> body) {
        requireEnabled();
        String supplier = text(body.get("supplier_name"), 160, true);
        String invoice = text(body.get("invoice_number"), 80, false);
        String note = text(body.get("note"), 500, false);
        Object input = body.get("items");
        if (!(input instanceof List) || ((List<?>)input).isEmpty() || ((List<?>)input).size() > 100)
            throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        List<Map<String,Object>> lines = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Object value : (List<?>)input) {
            if (!(value instanceof Map)) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            Map<?,?> item = (Map<?,?>)value;
            long product = positive(item.get("product_id")), qty = positive(item.get("quantity")), location = positive(item.get("location_id"));
            if (!seen.add(product)) throw new WarehouseException("WAREHOUSE_DUPLICATE_PRODUCT");
            lines.add(Map.of("product_id",product,"quantity",qty,"location_id",location));
        }
        lines.sort(Comparator.comparingLong(x -> ((Number)x.get("product_id")).longValue()));
        long id = insert("INSERT INTO warehouse_receipts(supplier_name,invoice_number,note,status,created_by,created_at) " +
                "VALUES (?,?,?,'DRAFT',?,UTC_TIMESTAMP())",supplier,invoice,note,actor());
        for (var line : lines) {
            Map<String,Object> product = one("SELECT name,is_active FROM products WHERE id=? FOR UPDATE",line.get("product_id"));
            if (!active(product.get("is_active"))) throw new WarehouseException("WAREHOUSE_PRODUCT_INACTIVE");
            one("SELECT id FROM warehouse_locations WHERE id=? AND is_active=1 FOR UPDATE",line.get("location_id"));
            jdbc.update("INSERT INTO warehouse_receipt_items(receipt_id,product_id,product_name,quantity,location_id) VALUES (?,?,?,?,?)",
                    id,line.get("product_id"),product.get("name"),line.get("quantity"),line.get("location_id"));
        }
        return id;
    }

    @Transactional public long postReceipt(long id) {
        requireEnabled();
        Map<String,Object> header = one("SELECT status FROM warehouse_receipts WHERE id=? FOR UPDATE",id);
        if ("POSTED".equals(header.get("status"))) return id;
        if (!"DRAFT".equals(header.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        List<Map<String,Object>> lines = receiptLines(id);
        if (lines.isEmpty()) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        for (var line : lines) {
            long product = ((Number)line.get("product_id")).longValue(), qty = ((Number)line.get("quantity")).longValue();
            Map<String,Object> row = one("SELECT stock_quantity,is_active FROM products WHERE id=? FOR UPDATE",product);
            if (!active(row.get("is_active"))) throw new WarehouseException("WAREHOUSE_PRODUCT_INACTIVE");
            one("SELECT id FROM warehouse_locations WHERE id=? AND is_active=1 FOR UPDATE",line.get("location_id"));
            if (((Number)row.get("stock_quantity")).longValue() + qty > Integer.MAX_VALUE)
                throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            jdbc.update("UPDATE products SET stock_quantity=stock_quantity+?,updated_at=NOW() WHERE id=?",qty,product);
            recordChange(product,qty,"RECEIPT","KR-"+id,actor(),"", "receipt:"+id+":"+product);
        }
        jdbc.update("UPDATE warehouse_receipts SET status='POSTED',posted_by=?,posted_at=UTC_TIMESTAMP() WHERE id=?",actor(),id);
        return id;
    }
    private List<Map<String,Object>> receiptLines(long id) {
        return jdbc.queryForList("SELECT product_id,quantity,location_id FROM warehouse_receipt_items WHERE receipt_id=? ORDER BY product_id",id);
    }
    @Transactional public long reverseReceipt(long id, String reason) {
        requireEnabled();
        reason = text(reason,500,true);
        Map<String,Object> header = one("SELECT status FROM warehouse_receipts WHERE id=? FOR UPDATE",id);
        if ("REVERSED".equals(header.get("status"))) return id;
        if (!"POSTED".equals(header.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        for (var line : receiptLines(id)) {
            long product = ((Number)line.get("product_id")).longValue(), qty = ((Number)line.get("quantity")).longValue();
            lockStock(product);
            if (jdbc.update("UPDATE products SET stock_quantity=stock_quantity-?,updated_at=NOW() WHERE id=? AND stock_quantity>=?",qty,product,qty)!=1)
                throw new WarehouseException("WAREHOUSE_INSUFFICIENT_STOCK");
            recordChange(product,-qty,"RECEIPT_REVERSAL","KR-"+id,actor(),reason,"reversal:"+id+":"+product);
        }
        jdbc.update("UPDATE warehouse_receipts SET status='REVERSED',reversed_by=?,reversed_at=UTC_TIMESTAMP(),reversal_reason=? WHERE id=?",actor(),reason,id);
        return id;
    }

    /** Caller holds the product row lock until commit, so stock_after follows actual commit order. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordChange(long product, long delta, String kind, String reference, String actor, String note, String eventKey) {
        if (!enabled || (delta == 0 && !"OPENING".equals(kind))) return;
        Map<String,Object> row = one("SELECT name,stock_quantity FROM products WHERE id=? FOR UPDATE",product);
        jdbc.update("INSERT INTO warehouse_stock_movements(product_id,product_name,kind,quantity_change,stock_after,reference,actor,note,event_key,created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",product,row.get("name"),kind,delta,row.get("stock_quantity"),reference,actor,note,eventKey);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public long lockStock(long product) {
        return ((Number)one("SELECT stock_quantity FROM products WHERE id=? FOR UPDATE",product).get("stock_quantity")).longValue();
    }
    public void requireDocumentStock(Map<String,Object> body) {
        if (enabled && body.containsKey("stock_quantity")) throw new WarehouseException("WAREHOUSE_USE_RECEIPTS");
    }

    public Map<String,Object> preparation(int page, int size) {
        return page("FROM orders o LEFT JOIN delivery_jobs j ON j.order_id=o.id " +
                "WHERE o.payment_status='PAID' AND o.status NOT IN ('CANCELLED','DELIVERED') " +
                "AND (j.id IS NULL OR j.status IN ('WAITING_ASSIGNMENT','PENDING'))",
                "o.id,o.order_number,o.status,o.payment_status,o.total_price," +
                "DATE_FORMAT(o.ordered_at,'%Y-%m-%d %H:%i:%s') AS ordered_at," +
                "(SELECT COALESCE(SUM(i.quantity),0) FROM order_items i WHERE i.order_id=o.id) AS quantity," +
                "j.status AS delivery_status", "ORDER BY o.ordered_at,o.id",page,size);
    }
    public List<Map<String,Object>> preparationItems(long id) {
        requireEnabled();
        one("SELECT id FROM orders WHERE id=? AND payment_status='PAID'",id);
        return jdbc.queryForList("SELECT i.product_id,i.product_name,i.quantity,p.barcode FROM order_items i " +
                "LEFT JOIN products p ON p.id=i.product_id WHERE i.order_id=? ORDER BY i.id",id);
    }
}
