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

    @Transactional(propagation=Propagation.MANDATORY)
    public boolean needsPhysicalReturn(long id) {
        var order=one("SELECT status FROM orders WHERE id=? FOR UPDATE",id);
        return Set.of("RETURNING","RETURNED").contains(order.get("status")) || ("DELIVERED".equals(order.get("status")) && jdbc.queryForObject("SELECT COUNT(*) FROM delivery_partial_returns WHERE order_id=?",Integer.class,id)==1);
    }
    @Value("${warehouse.inventory-enabled:false}") private boolean inventoryEnabled;
    public boolean isEnabled() { return enabled; }
    public boolean isInventoryEnabled(){return enabled&&inventoryEnabled;}
    public long sellableAvailable(long product,long legacy){return isInventoryEnabled()&&new BatchStock(jdbc).active(product)?new BatchStock(jdbc).available(product):legacy;}
    @Transactional(propagation=Propagation.MANDATORY) public boolean canSell(long product,long quantity){long legacy=lockStock(product);return sellableAvailable(product,legacy)>=quantity;}
    @Transactional(propagation=Propagation.MANDATORY) public void issueOrderStock(long order){if(isInventoryEnabled())new BatchStock(jdbc).issue(order,actor());}
    public void requireFreshInventory(long order){if(isInventoryEnabled())new BatchStock(jdbc).requireFresh(order);}
    public List<Map<String,Object>> allocations(long order){if(!isInventoryEnabled())return List.of();return jdbc.queryForList("SELECT a.product_id,a.batch_id,a.quantity-a.issued_quantity-a.released_quantity held_quantity,b.batch_number,b.expiry_date,l.code location_code FROM inventory_allocations a JOIN inventory_batches b ON b.id=a.batch_id JOIN warehouse_locations l ON l.id=b.location_id WHERE a.order_id=? ORDER BY l.code,b.expiry_date,b.id",order);}
    @Transactional(propagation=Propagation.MANDATORY) public void recordInventoryAdjustment(long product,long delta,String kind,String reference,String note){
        if(delta==0)return;
        var row=one("SELECT name,stock_quantity FROM products WHERE id=? FOR UPDATE",product);
        jdbc.update("INSERT INTO warehouse_stock_movements(product_id,product_name,kind,quantity_change,stock_after,reference,actor,note,event_key,created_at) VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",product,row.get("name"),kind,delta,row.get("stock_quantity"),reference,actor(),note,"inventory:"+UUID.randomUUID());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void reconcilePartialRefund(long orderId) {
        // This records an authenticated provider callback, never initiates a payment/refund.
        jdbc.update("UPDATE delivery_partial_returns SET refund_status='FULL_PAYMENT_REFUND' WHERE order_id=? AND refund_status='PENDING'",orderId);
    }

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
    static long nonnegative(Object value) {
        try { long n=new BigDecimal(String.valueOf(value)).longValueExact(); if(n<0||n>Integer.MAX_VALUE) throw new NumberFormatException(); return n; }
        catch (Exception ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
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
        return jdbc.queryForList("SELECT l.id,l.code,l.name,l.type,l.is_active,l.hierarchy_level,l.parent_id,CONCAT_WS(' / ',g.code,p.code,l.code) path FROM warehouse_locations l LEFT JOIN warehouse_locations p ON p.id=l.parent_id LEFT JOIN warehouse_locations g ON g.id=p.parent_id ORDER BY path");
    }
    @Transactional public long createLocation(Map<String,Object> body) {
        requireEnabled();
        String code = text(body.get("code"), 40, true).toUpperCase(Locale.ROOT);
        String name = text(body.get("name"), 120, true);
        String type = text(body.get("type"), 20, true);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]*") || !Set.of("SHELF", "RECEIVING","COLD_STORAGE","DAMAGED","PICKING","RETURNS").contains(type))
            throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        String level=text(body.getOrDefault("hierarchy_level","BIN"),10,true);
        if(!Set.of("ZONE","ROW","BIN").contains(level))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        Long parent=body.get("parent_id")==null?null:positive(body.get("parent_id"));
        if((level.equals("ZONE")&&parent!=null)||(level.equals("ROW")&&parent==null))throw new WarehouseException("WAREHOUSE_INVALID_HIERARCHY");
        if(parent!=null) {
            var row=one("SELECT hierarchy_level FROM warehouse_locations WHERE id=? AND is_active=1 FOR UPDATE",parent);
            if(!Objects.equals(row.get("hierarchy_level"),level.equals("ROW")?"ZONE":"ROW"))throw new WarehouseException("WAREHOUSE_INVALID_HIERARCHY");
        }
        return insert("INSERT INTO warehouse_locations(code,name,type,hierarchy_level,parent_id,created_by,created_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP())",
                code, name, type, level, parent, actor());
    }

    public Map<String,Object> stock(String search, int page, int size) {
        String q = "%" + text(search, 120, false) + "%";
        return page("FROM products p WHERE (p.name LIKE ? OR p.barcode LIKE ? OR p.sku LIKE ?)",
                "p.id,p.name,p.sku,p.barcode,p.stock_quantity,p.is_active", "ORDER BY p.name,p.id", page, size, q,q,q);
    }
    public Map<String,Object> movements(Long productId, int page, int size) {
        return movements(productId,page,size,null,null);
    }
    public Map<String,Object> movements(Long productId, int page, int size, String dateFrom, String dateTo) {
        var dates=com.ninimum.api.admin.management.service.OrderListFilters.parse(null,dateFrom,dateTo);
        String from = "FROM warehouse_stock_movements m LEFT JOIN products p ON p.id=m.product_id WHERE 1=1";
        List<Object> args=new ArrayList<>();
        if (productId != null) {from += " AND m.product_id=?";args.add(productId);}
        if(dates.fromUtc()!=null){from+=" AND m.created_at>=?";args.add(dates.fromUtc());}
        if(dates.toUtc()!=null){from+=" AND m.created_at<?";args.add(dates.toUtc());}
        return page(from, "m.id,m.product_id,COALESCE(p.name,m.product_name) AS product_name,m.kind,m.quantity_change," +
                "m.stock_after,m.reference,m.actor,m.note,DATE_FORMAT(m.created_at," + UTC + ") AS created_at",
                "ORDER BY m.id DESC", page, size, args.toArray());
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
        Object image=result.remove("invoice_image");
        if(image instanceof byte[])result.put("invoice_image_data","data:image/png;base64,"+Base64.getEncoder().encodeToString((byte[])image));
        result.put("items",jdbc.queryForList("SELECT i.id,i.product_id,i.product_name,i.quantity,i.location_id,i.unit_price,DATE_FORMAT(i.manufacture_date,'%Y-%m-%d') AS manufacture_date,DATE_FORMAT(i.expiry_date,'%Y-%m-%d') AS expiry_date,i.input_unit,i.input_quantity,i.units_per_block,i.batch_number,l.code AS location_code " +
                "FROM warehouse_receipt_items i JOIN warehouse_locations l ON l.id=i.location_id WHERE i.receipt_id=? ORDER BY i.id",id));
        // Q-4 notes (column added by 20261022_receipt_condition.sql; skipped before that migration).
        try{var notes=new HashMap<Long,Object>();for(var n:jdbc.queryForList("SELECT product_id,price_note FROM warehouse_receipt_items WHERE receipt_id=? AND price_note IS NOT NULL",id))notes.put(((Number)n.get("product_id")).longValue(),n.get("price_note"));
            for(var item:(List<Map<String,Object>>)result.get("items"))if(notes.containsKey(((Number)item.get("product_id")).longValue()))item.put("price_note",notes.get(((Number)item.get("product_id")).longValue()));}
        catch(org.springframework.dao.DataAccessException ex){/* not migrated yet */}
        if(!"DRAFT".equals(result.get("status"))) {
            var counted=new HashMap<Long,Map<String,Object>>();
            for(var c:jdbc.queryForList("SELECT * FROM warehouse_receipt_counts WHERE receipt_id=?",id))counted.put(((Number)c.get("product_id")).longValue(),c);
            for(var item:(List<Map<String,Object>>)result.get("items")){var c=counted.get(((Number)item.get("product_id")).longValue());if(c==null)continue;
                item.put("fit_quantity",c.get("counted_quantity"));item.put("unfit_quantity",c.get("unfit_quantity"));item.put("unfit_condition",c.get("unfit_condition"));
                if(c.get("unfit_photo") instanceof byte[] photo)item.put("unfit_photo_data","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(photo));}
        }
        if("DRAFT".equals(result.get("status"))) {
            for(var item:(List<Map<String,Object>>)result.get("items")){item.remove("quantity");item.remove("input_quantity");}
        }
        return result;
    }

    @Transactional public long createReceipt(Map<String,Object> body) {
        requireEnabled();
        com.ninimum.api.finance.PeriodClose.guardToday();
        Long supplierId = body.get("supplier_id")==null ? null : positive(body.get("supplier_id"));
        Map<String,Object> registeredSupplier = supplierId==null ? null : one("SELECT name,payment_term_days FROM suppliers WHERE id=? AND is_active=1 FOR UPDATE",supplierId);
        String supplier = registeredSupplier==null ? text(body.get("supplier_name"),160,true) : String.valueOf(registeredSupplier.get("name"));
        BigDecimal invoiceAmount = supplierId==null ? null : SupplierService.invoiceAmount(body.get("invoice_amount"));
        java.time.LocalDate invoiceDate = supplierId==null ? null : SupplierService.invoiceDate(body.get("invoice_date"));
        String invoice = text(body.get("invoice_number"), 80, false);
        if(supplierId!=null && invoice.isEmpty()) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        String note = text(body.get("note"), 500, false);
        byte[] image=ReceiptImage.decode(body.get("invoice_image_base64"));
        Object input = body.get("items");
        if (!(input instanceof List) || ((List<?>)input).isEmpty() || ((List<?>)input).size() > 100)
            throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        List<Map<String,Object>> lines = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Object value : (List<?>)input) {
            if (!(value instanceof Map)) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            Map<?,?> item = (Map<?,?>)value;
            long product = positive(item.get("product_id")), location = positive(item.get("location_id"));
            ReceiptMetadata metadata=ReceiptMetadata.parse(item,java.time.Clock.systemUTC());
            long qty=metadata.pieces();
            if (!seen.add(product)) throw new WarehouseException("WAREHOUSE_DUPLICATE_PRODUCT");
            // Q-4: a purchase price far from the previous one needs a note.
            String priceNote=text(item.get("price_note"),500,false);
            if(priceNote.isEmpty()&&priceJump(product,metadata.unitPrice())&&com.ninimum.api.admin.access.StrictRules.on("Q-4"))throw new WarehouseException("RECEIPT_PRICE_NOTE_REQUIRED");
            lines.add(Map.of("product_id",product,"quantity",qty,"location_id",location,"metadata",metadata,"price_note",priceNote));
        }
        lines.sort(Comparator.comparingLong(x -> ((Number)x.get("product_id")).longValue()));
        long id = insert("INSERT INTO warehouse_receipts(supplier_name,invoice_number,note,status,created_by,created_at,invoice_image) " +
                "VALUES (?,?,?,'DRAFT',?,UTC_TIMESTAMP(),?)",supplier,invoice,note,actor(),image);
        for (var line : lines) {
            Map<String,Object> product = one("SELECT name,is_active FROM products WHERE id=? FOR UPDATE",line.get("product_id"));
            if (!active(product.get("is_active"))) throw new WarehouseException("WAREHOUSE_PRODUCT_INACTIVE");
            one("SELECT id FROM warehouse_locations WHERE id=? AND is_active=1 AND hierarchy_level='BIN' FOR UPDATE",line.get("location_id"));
            ReceiptMetadata metadata=(ReceiptMetadata)line.get("metadata");
            String priceNote=(String)line.get("price_note");
            if(priceNote.isEmpty())jdbc.update("INSERT INTO warehouse_receipt_items(receipt_id,product_id,product_name,quantity,location_id,unit_price,manufacture_date,expiry_date,input_unit,input_quantity,units_per_block,batch_number) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,line.get("product_id"),product.get("name"),line.get("quantity"),line.get("location_id"),metadata.unitPrice(),metadata.manufactured(),metadata.expiry(),metadata.unit(),metadata.inputQuantity(),metadata.unitsPerBlock(),metadata.batch());
            else jdbc.update("INSERT INTO warehouse_receipt_items(receipt_id,product_id,product_name,quantity,location_id,unit_price,manufacture_date,expiry_date,input_unit,input_quantity,units_per_block,batch_number,price_note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,line.get("product_id"),product.get("name"),line.get("quantity"),line.get("location_id"),metadata.unitPrice(),metadata.manufactured(),metadata.expiry(),metadata.unit(),metadata.inputQuantity(),metadata.unitsPerBlock(),metadata.batch(),priceNote);
        }
        if(supplierId!=null) jdbc.update("INSERT INTO supplier_invoices(receipt_id,supplier_id,invoice_number,invoice_amount,invoice_date,due_date,created_by,created_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP())",
                id,supplierId,invoice,invoiceAmount,invoiceDate,invoiceDate.plusDays(((Number)registeredSupplier.get("payment_term_days")).longValue()),actor());
        return id;
    }

    /** The last posted purchase price per piece for each product (Q-4). */
    public Map<String,Object> previousCosts(List<Long> products) {
        requireEnabled();
        var costs=new LinkedHashMap<String,Object>();
        for(long product:products.stream().distinct().limit(100).toList()){BigDecimal last=lastCost(product);if(last!=null)costs.put(String.valueOf(product),last);}
        return Map.of("threshold_percent",priceJumpPercent(),"note_required",com.ninimum.api.admin.access.StrictRules.on("Q-4"),"costs",costs);
    }
    private BigDecimal lastCost(long product) {
        var rows=jdbc.queryForList("SELECT i.unit_price FROM warehouse_receipt_items i JOIN warehouse_receipts r ON r.id=i.receipt_id WHERE i.product_id=? AND r.status='POSTED' AND i.unit_price IS NOT NULL ORDER BY r.posted_at DESC,r.id DESC LIMIT 1",BigDecimal.class,product);
        return rows.isEmpty()?null:rows.get(0);
    }
    private int priceJumpPercent() {
        try{return Integer.parseInt(String.valueOf(com.ninimum.api.admin.access.StrictRules.value("Q-4")));}catch(NumberFormatException ex){return 20;}
    }
    private boolean priceJump(long product,BigDecimal price) {
        BigDecimal last=lastCost(product);
        if(last==null||last.signum()<=0||price==null)return false;
        return price.subtract(last).abs().multiply(BigDecimal.valueOf(100)).compareTo(last.multiply(BigDecimal.valueOf(priceJumpPercent())))>0;
    }

    @Transactional public void updateReceiptMetadata(long id,long product,Map<String,Object> body) {
        requireEnabled();
        var header=one("SELECT status FROM warehouse_receipts WHERE id=? FOR UPDATE",id);
        if(!"DRAFT".equals(header.get("status")))throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        var input=new HashMap<String,Object>(body);input.put("quantity",1);input.put("input_unit","PIECE");
        var metadata=ReceiptMetadata.parse(input,java.time.Clock.systemUTC());
        if(jdbc.update("UPDATE warehouse_receipt_items SET unit_price=?,manufacture_date=?,expiry_date=?,batch_number=?,metadata_updated_by=?,metadata_updated_at=UTC_TIMESTAMP() WHERE receipt_id=? AND product_id=?",metadata.unitPrice(),metadata.manufactured(),metadata.expiry(),metadata.batch(),actor(),id,product)!=1)throw new WarehouseException("WAREHOUSE_NOT_FOUND");
    }

    @Deprecated
    @Transactional public long postReceipt(long id) { return postReceipt(id,Map.of()); }
    @Transactional public long postReceipt(long id,Map<String,Object> body) {
        requireEnabled();
        Map<String,Object> header = one("SELECT status,created_by FROM warehouse_receipts WHERE id=? FOR UPDATE",id);
        if ("POSTED".equals(header.get("status"))) return id;
        if (!"DRAFT".equals(header.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        com.ninimum.api.finance.PeriodClose.guardToday();
        List<Map<String,Object>> lines = receiptLines(id);
        if (lines.isEmpty()) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        var auth=SecurityContextHolder.getContext().getAuthentication();
        boolean owner=auth!=null&&auth.getAuthorities().stream().anyMatch(a->"SUPER_ADMIN".equals(a.getAuthority()));
        if(actor().equals(header.get("created_by"))&&!owner)throw new WarehouseException("WAREHOUSE_SEPARATE_APPROVER_REQUIRED");
        if(!(body.get("counts") instanceof List<?> counts)||counts.size()!=lines.size())throw new WarehouseException("WAREHOUSE_BLIND_COUNT_REQUIRED");
        var counted=new HashMap<Long,Long>();
        var unfit=new HashMap<Long,Object[]>();
        for(Object raw:counts) {
            if(!(raw instanceof Map<?,?> c))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            long product=positive(c.get("product_id")),quantity=nonnegative(c.get("quantity")),bad=c.get("unfit_quantity")==null?0:nonnegative(c.get("unfit_quantity"));
            if(counted.put(product,quantity)!=null)throw new WarehouseException("WAREHOUSE_DUPLICATE_PRODUCT");
            // Q-3: unfit goods (damaged / expired / wrong product) are recorded with a photo and never become stock.
            if(bad>0){
                String condition=text(c.get("unfit_condition"),20,true);
                if(!Set.of("DAMAGED","EXPIRED","WRONG_PRODUCT").contains(condition))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
                byte[] photo=ReceiptImage.decode(c.get("unfit_photo_base64"));
                if(photo==null&&com.ninimum.api.admin.access.StrictRules.on("Q-3"))throw new WarehouseException("RECEIPT_PHOTO_REQUIRED");
                unfit.put(product,new Object[]{bad,condition,photo});
            }
            if(quantity+bad==0)throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        }
        for(var line:lines) {
            Object expiry=line.get("expiry_date");
            if(line.get("unit_price")==null||expiry==null)throw new WarehouseException("WAREHOUSE_RECEIPT_METADATA_REQUIRED");
            if(!java.time.LocalDate.parse(expiry.toString()).isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tashkent"))))throw new WarehouseException("WAREHOUSE_EXPIRED_BATCH");
        }
        for(var line:lines){long product=((Number)line.get("product_id")).longValue();Object[] bad=unfit.get(product);
            if(counted.get(product)==null||counted.get(product)+(bad==null?0:(Long)bad[0])!=((Number)line.get("quantity")).longValue())throw new WarehouseException("WAREHOUSE_COUNT_MISMATCH");}

        for (var line : lines) {
            long product = ((Number)line.get("product_id")).longValue(), qty = counted.get(product);
            Map<String,Object> row = one("SELECT stock_quantity,is_active FROM products WHERE id=? FOR UPDATE",product);
            if (!active(row.get("is_active"))) throw new WarehouseException("WAREHOUSE_PRODUCT_INACTIVE");
            one("SELECT id FROM warehouse_locations WHERE id=? AND is_active=1 AND hierarchy_level='BIN' FOR UPDATE",line.get("location_id"));
            if (((Number)row.get("stock_quantity")).longValue() + qty > Integer.MAX_VALUE)
                throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            jdbc.update("UPDATE products SET stock_quantity=stock_quantity+?,updated_at=NOW() WHERE id=?",qty,product);
            recordChange(product,qty,"RECEIPT","KR-"+id,actor(),"", "receipt:"+id+":"+product);
        }
        for(var entry:counted.entrySet()){Object[] bad=unfit.get(entry.getKey());
            if(bad==null)jdbc.update("INSERT INTO warehouse_receipt_counts(receipt_id,product_id,counted_quantity,counted_by,counted_at) VALUES(?,?,?,?,UTC_TIMESTAMP())",id,entry.getKey(),entry.getValue(),actor());
            else jdbc.update("INSERT INTO warehouse_receipt_counts(receipt_id,product_id,counted_quantity,counted_by,counted_at,unfit_quantity,unfit_condition,unfit_photo) VALUES(?,?,?,?,UTC_TIMESTAMP(),?,?,?)",id,entry.getKey(),entry.getValue(),actor(),bad[0],bad[1],bad[2]);}
        jdbc.update("UPDATE warehouse_receipts SET status='POSTED',posted_by=?,posted_at=UTC_TIMESTAMP() WHERE id=?",actor(),id);
        return id;
    }
    private List<Map<String,Object>> receiptLines(long id) {
        return jdbc.queryForList("SELECT product_id,quantity,location_id,unit_price,expiry_date FROM warehouse_receipt_items WHERE receipt_id=? ORDER BY product_id",id);
    }
    @Transactional public long reverseReceipt(long id, String reason) {
        requireEnabled();
        reason = text(reason,500,true);
        Map<String,Object> header = one("SELECT status FROM warehouse_receipts WHERE id=? FOR UPDATE",id);
        if ("REVERSED".equals(header.get("status"))) return id;
        if (!"POSTED".equals(header.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        // Q-16: a receipt posted in a closed period stays; the reversal itself is dated today.
        com.ninimum.api.finance.PeriodClose.guard(jdbc.queryForObject("SELECT DATE(DATE_ADD(posted_at,INTERVAL 5 HOUR)) FROM warehouse_receipts WHERE id=?",java.time.LocalDate.class,id));
        com.ninimum.api.finance.PeriodClose.guardToday();
        for (var line : jdbc.queryForList("SELECT i.product_id,COALESCE(c.counted_quantity,i.quantity) quantity FROM warehouse_receipt_items i LEFT JOIN warehouse_receipt_counts c ON c.receipt_id=i.receipt_id AND c.product_id=i.product_id WHERE i.receipt_id=? ORDER BY i.product_id",id)) {
            long product = ((Number)line.get("product_id")).longValue(), qty = ((Number)line.get("quantity")).longValue();
            if (qty == 0) continue;
            lockStock(product);
            if(isInventoryEnabled()&&new BatchStock(jdbc).active(product)){
                long delta=new BatchStock(jdbc).reverse(product,id,qty,actor(),reason);
                recordInventoryAdjustment(product,delta,"RECEIPT_REVERSAL","KR-"+id,reason);
            }else{
                if (jdbc.update("UPDATE products SET stock_quantity=stock_quantity-?,updated_at=NOW() WHERE id=? AND stock_quantity>=?",qty,product,qty)!=1)throw new WarehouseException("WAREHOUSE_INSUFFICIENT_STOCK");
                recordChange(product,-qty,"RECEIPT_REVERSAL","KR-"+id,actor(),reason,"reversal:"+id+":"+product);
            }
        }
        jdbc.update("UPDATE warehouse_receipts SET status='REVERSED',reversed_by=?,reversed_at=UTC_TIMESTAMP(),reversal_reason=? WHERE id=?",actor(),reason,id);
        return id;
    }

    /** Caller holds the product row lock until commit, so stock_after follows actual commit order. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordChange(long product, long delta, String kind, String reference, String actor, String note, String eventKey) {
        if (!enabled || (delta == 0 && !"OPENING".equals(kind))) return;
        if(isInventoryEnabled())delta=new BatchStock(jdbc).reconcile(product,delta,kind,reference,actor);
        Map<String,Object> row = one("SELECT name,stock_quantity FROM products WHERE id=? FOR UPDATE",product);
        jdbc.update("INSERT INTO warehouse_stock_movements(product_id,product_name,kind,quantity_change,stock_after,reference,actor,note,event_key,created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",product,row.get("name"),kind,delta,row.get("stock_quantity"),reference,actor,note,eventKey);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public long lockStock(long product) {
        one("SELECT id FROM products WHERE id=? FOR UPDATE",product);
        if(isInventoryEnabled())new BatchStock(jdbc).refresh(product);
        return ((Number)one("SELECT stock_quantity FROM products WHERE id=?",product).get("stock_quantity")).longValue();
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
