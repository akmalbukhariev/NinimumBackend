package com.ninimum.api.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WarehouseAppService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final WarehouseService warehouse;
    @Value("${warehouse.preparation-enabled:false}") private boolean enabled;
    public boolean isEnabled() { return enabled; }
    @PostConstruct public void verifySchema() {
        if (enabled && (!warehouse.isEnabled() || !Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT version FROM warehouse_app_schema WHERE id=1",Integer.class))))
            throw new IllegalStateException("Run warehouse-step2.sql and enable warehouse.enabled first");
    }
    private void requireEnabled() { if (!enabled) throw new WarehouseException("WAREHOUSE_PREPARATION_DISABLED"); }
    private Map<String,Object> one(String sql,Object... args) {
        var rows=jdbc.queryForList(sql,args);
        if (rows.isEmpty()) throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        return rows.get(0);
    }
    private String code(Object value) {
        String code=WarehouseService.text(value,50,true).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9._-]{3,50}")) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        return code;
    }
    private String validPassword(Object value) {
        if(!(value instanceof String)) throw new WarehouseException("WAREHOUSE_PASSWORD_SHORT");
        String password=(String)value;
        if(password.length()<6) throw new WarehouseException("WAREHOUSE_PASSWORD_SHORT");
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        return password;
    }
    public List<Map<String,Object>> workers() {
        requireEnabled();
        return jdbc.queryForList("SELECT id,worker_code,full_name,status FROM warehouse_workers ORDER BY id DESC");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void createWorker(Map<String,Object> body) {
        requireEnabled();String code=code(body.get("worker_code"));
        String name=WarehouseService.text(body.get("full_name"),120,true);
        String password=validPassword(body.get("password"));
        jdbc.update("INSERT INTO warehouse_workers(worker_code,full_name,password_hash,created_at,updated_at) VALUES (?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",
                code,name,passwords.encode(password));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void updateWorker(long id,Map<String,Object> body) {
        requireEnabled();var worker=one("SELECT worker_code FROM warehouse_workers WHERE id=? FOR UPDATE",id);
        if (body.containsKey("status")) {
            String status=WarehouseService.text(body.get("status"),20,true);
            if (!Set.of("ACTIVE","INACTIVE").contains(status)) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
            jdbc.update("UPDATE warehouse_workers SET status=?,updated_at=UTC_TIMESTAMP() WHERE id=?",status,id);
        }
        if (body.containsKey("password")) {
            String password=validPassword(body.get("password"));
            jdbc.update("UPDATE warehouse_workers SET password_hash=?,updated_at=UTC_TIMESTAMP() WHERE id=?",passwords.encode(password),id);
        }
        jdbc.update("DELETE FROM warehouse_app_sessions WHERE worker_code=?",worker.get("worker_code"));
    }
    public Map<String,Object> worker(String code) {
        requireEnabled();return one("SELECT worker_code,full_name,status FROM warehouse_workers WHERE worker_code=? AND status='ACTIVE'",code);
    }
    private static String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex) {throw new IllegalStateException(ex);}
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> login(String input,String password, java.util.function.Function<String,String> tokens) {
        requireEnabled();String code=code(input);
        var worker=one("SELECT worker_code,full_name,status,password_hash FROM warehouse_workers WHERE worker_code=? FOR UPDATE",code);
        if (!"ACTIVE".equals(worker.get("status")) || password==null || !passwords.matches(password,(String)worker.get("password_hash")))
            throw new WarehouseException("WAREHOUSE_LOGIN_FAILED");
        String token=tokens.apply(code);
        jdbc.update("INSERT INTO warehouse_app_sessions(worker_code,token_hash) VALUES (?,?) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash)",code,hash(token));
        return Map.of("token",token,"worker_code",code,"full_name",worker.get("full_name"));
    }
    public boolean isCurrent(String code,String token) {
        if (!enabled) return false;
        var rows=jdbc.queryForList("SELECT s.token_hash FROM warehouse_app_sessions s JOIN warehouse_workers w ON w.worker_code=s.worker_code WHERE s.worker_code=? AND w.status='ACTIVE'",code);
        return rows.size()==1 && hash(token).equals(rows.get(0).get("token_hash"));
    }
    public void logout(String code,String token) {
        requireEnabled();jdbc.update("DELETE FROM warehouse_app_sessions WHERE worker_code=? AND token_hash=?",code,hash(token));
    }
    private static final String ELIGIBLE="o.payment_status='PAID' AND o.status IN ('CONFIRMED','PREPARING') AND NOT EXISTS (SELECT 1 FROM delivery_jobs j WHERE j.order_id=o.id AND j.status NOT IN ('WAITING_ASSIGNMENT','PENDING'))";
    public Map<String,Object> orders(String code,String view,int page,int size,boolean admin) {
        requireEnabled();
        int p=Math.max(1,Math.min(page,1000000)),s=Math.max(1,Math.min(size,100));
        String from="FROM orders o LEFT JOIN warehouse_preparations wp ON wp.order_id=o.id ";
        List<Object> args=new ArrayList<>();
        if (admin) from+="WHERE ("+ELIGIBLE+" OR wp.order_id IS NOT NULL) ";
        else if ("history".equals(view)) {from+="WHERE wp.status='READY' AND wp.worker_code=? ";args.add(code);}
        else if ("mine".equals(view)) {from+="WHERE "+ELIGIBLE+" AND wp.status IN ('PICKING','BLOCKED') AND wp.worker_code=? ";args.add(code);}
        else from+="WHERE "+ELIGIBLE+" AND (wp.status IS NULL OR wp.status='WAITING') ";
        long total=jdbc.queryForObject("SELECT COUNT(*) "+from,Long.class,args.toArray());args.add(s);args.add((p-1)*s);
        var rows=jdbc.queryForList("SELECT o.id,o.order_number,o.status AS order_status,o.payment_status,COALESCE(wp.status,'WAITING') AS preparation_status,wp.worker_code,wp.note,"+
            "DATE_FORMAT(wp.ready_at,'%Y-%m-%dT%H:%i:%sZ') AS ready_at,DATE_FORMAT(o.ordered_at,'%Y-%m-%d %H:%i:%s') AS ordered_at,"+
            "(SELECT COALESCE(SUM(quantity),0) FROM order_items i WHERE i.order_id=o.id) AS quantity "+from+"ORDER BY o.ordered_at "+(admin || "history".equals(view)?"DESC":"ASC")+",o.id LIMIT ? OFFSET ?",args.toArray());
        return Map.of("items",rows,"total",total,"page",p,"page_size",s);
    }
    public Map<String,Object> detail(long id) {
        requireEnabled();var row=new LinkedHashMap<>(one("SELECT o.id,o.order_number,o.payment_status,o.status AS order_status,COALESCE(wp.status,'WAITING') AS preparation_status,wp.worker_code,wp.note "+
            "FROM orders o LEFT JOIN warehouse_preparations wp ON wp.order_id=o.id WHERE o.id=?",id));
        var items=jdbc.queryForList("SELECT wi.product_id,wi.product_name,wi.barcode,wi.required_quantity,wi.checked_quantity," +
            "(SELECT MAX(NULLIF(oi.product_image_url,'')) FROM order_items oi WHERE oi.order_id=wi.order_id AND oi.product_id=wi.product_id) AS product_image_url " +
            "FROM warehouse_preparation_items wi WHERE wi.order_id=? ORDER BY wi.product_id",id);
        if(items.isEmpty()) items=jdbc.queryForList("SELECT i.product_id,MAX(i.product_name) AS product_name,MAX(NULLIF(i.product_image_url,'')) AS product_image_url,p.barcode,SUM(i.quantity) AS required_quantity,0 AS checked_quantity FROM order_items i LEFT JOIN products p ON p.id=i.product_id WHERE i.order_id=? GROUP BY i.product_id,p.barcode ORDER BY i.product_id",id);
        row.put("items",items);
        row.put("events",jdbc.queryForList("SELECT actor,kind,product_id,quantity,note,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%sZ') AS created_at FROM warehouse_preparation_events WHERE order_id=? ORDER BY id DESC LIMIT 100",id));
        return row;
    }
    private void lockOrder(long id) {
        var order=one("SELECT payment_status,status FROM orders WHERE id=? FOR UPDATE",id);
        if (!"PAID".equals(order.get("payment_status")) || !Set.of("CONFIRMED","PREPARING").contains(order.get("status")))
            throw new WarehouseException("WAREHOUSE_ORDER_UNAVAILABLE");
        var jobs=jdbc.queryForList("SELECT status FROM delivery_jobs WHERE order_id=? FOR UPDATE",id);
        if(jobs.stream().anyMatch(x->!Set.of("WAITING_ASSIGNMENT","PENDING").contains(x.get("status"))))
            throw new WarehouseException("WAREHOUSE_ORDER_UNAVAILABLE");
    }
    private void event(long id,String code,String kind,Long product,Integer qty,String note) {
        jdbc.update("INSERT INTO warehouse_preparation_events(order_id,actor,kind,product_id,quantity,note,created_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP())",id,code,kind,product,qty,note);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> claim(long id,String code) {
        requireEnabled();lockOrder(id);worker(code);
        var existing=jdbc.queryForList("SELECT status,worker_code FROM warehouse_preparations WHERE order_id=? FOR UPDATE",id);
        if(existing.isEmpty()) jdbc.update("INSERT INTO warehouse_preparations(order_id,status,updated_at) VALUES (?,'WAITING',UTC_TIMESTAMP())",id);
        var row=one("SELECT status,worker_code FROM warehouse_preparations WHERE order_id=? FOR UPDATE",id);
        if (Set.of("PICKING","BLOCKED").contains(row.get("status")) && code.equals(row.get("worker_code"))) return detail(id);
        if (!"WAITING".equals(row.get("status"))) throw new WarehouseException("WAREHOUSE_ALREADY_CLAIMED");
        jdbc.update("INSERT INTO warehouse_preparation_items(order_id,product_id,product_name,barcode,required_quantity) SELECT ?,i.product_id,MAX(i.product_name),p.barcode,SUM(i.quantity) FROM order_items i LEFT JOIN products p ON p.id=i.product_id WHERE i.order_id=? GROUP BY i.product_id,p.barcode",id,id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_items WHERE order_id=?",Integer.class,id)==0)
            throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        jdbc.update("UPDATE warehouse_preparations SET status='PICKING',worker_code=?,note=NULL,started_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE order_id=?",code,id);
        event(id,code,"CLAIM",null,null,"");return detail(id);
    }
    private Map<String,Object> owned(long id,String code) {
        lockOrder(id);worker(code);
        var row=one("SELECT status,worker_code FROM warehouse_preparations WHERE order_id=? FOR UPDATE",id);
        if(!code.equals(row.get("worker_code"))) throw new WarehouseException("WAREHOUSE_NOT_OWNER");
        return row;
    }
    // Absolute checked quantities make a repeated request safe, including a retry after a lost response.
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> check(long id,String code,Map<String,Object> body) {
        requireEnabled();var row=owned(id,code);
        if(!"PICKING".equals(row.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        long product=WarehouseService.positive(body.get("product_id"));
        long qty=WarehouseService.positive(body.get("quantity"));
        var item=one("SELECT * FROM warehouse_preparation_items WHERE order_id=? AND product_id=? FOR UPDATE",id,product);
        if(qty>((Number)item.get("required_quantity")).longValue()) throw new WarehouseException("WAREHOUSE_TOO_MANY");
        String barcode=WarehouseService.text(body.get("barcode"),120,false),note="";
        String expected=Objects.toString(item.get("barcode"),"");
        if (expected.isBlank()) note=WarehouseService.text(body.get("reason"),500,true);
        else if(!expected.equals(barcode)) throw new WarehouseException("WAREHOUSE_BARCODE_MISMATCH");
        if(qty!=((Number)item.get("checked_quantity")).longValue()) {
            jdbc.update("UPDATE warehouse_preparation_items SET checked_quantity=? WHERE order_id=? AND product_id=?",qty,id,product);
            event(id,code,expected.isBlank()?"MANUAL_CHECK":"BARCODE_CHECK",product,(int)qty,note);
        }
        return detail(id);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> problem(long id,String code,Map<String,Object> body) {
        requireEnabled();var row=owned(id,code);
        if(!Set.of("PICKING","BLOCKED").contains(row.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        String reason=WarehouseService.text(body.get("reason"),500,true);
        jdbc.update("UPDATE warehouse_preparations SET status='BLOCKED',note=?,updated_at=UTC_TIMESTAMP() WHERE order_id=?",reason,id);
        event(id,code,"PROBLEM",null,null,reason);return detail(id);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> resume(long id,String code) {
        requireEnabled();var row=owned(id,code);
        if(!"BLOCKED".equals(row.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        jdbc.update("UPDATE warehouse_preparations SET status='PICKING',note=NULL,updated_at=UTC_TIMESTAMP() WHERE order_id=?",id);
        event(id,code,"RESUME",null,null,"");return detail(id);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> ready(long id,String code) {
        requireEnabled();var row=owned(id,code);
        if("READY".equals(row.get("status"))) return detail(id);
        if(!"PICKING".equals(row.get("status"))) throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        int missing=jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_items WHERE order_id=? AND checked_quantity<>required_quantity",Integer.class,id);
        if(missing!=0 || jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_items WHERE order_id=?",Integer.class,id)==0)
            throw new WarehouseException("WAREHOUSE_INCOMPLETE");
        jdbc.update("UPDATE warehouse_preparations SET status='READY',ready_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE order_id=?",id);
        jdbc.update("UPDATE orders SET status='PREPARING',updated_at=NOW() WHERE id=?",id);
        event(id,code,"READY",null,null,"");return detail(id);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void reset(long id,String actor,String reason) {
        requireEnabled();reason=WarehouseService.text(reason,500,true);lockOrder(id);
        one("SELECT order_id FROM warehouse_preparations WHERE order_id=? FOR UPDATE",id);
        jdbc.update("DELETE FROM warehouse_preparation_items WHERE order_id=?",id);
        jdbc.update("UPDATE warehouse_preparations SET status='WAITING',worker_code=NULL,note=NULL,started_at=NULL,ready_at=NULL,updated_at=UTC_TIMESTAMP() WHERE order_id=?",id);
        event(id,actor,"RESET",null,null,reason);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireReadyOrder(long id) {
        if(!enabled) return;
        var order=one("SELECT payment_status,status FROM orders WHERE id=? FOR UPDATE",id);
        if(!"PAID".equals(order.get("payment_status")) || "CANCELLED".equals(order.get("status"))) throw new WarehouseException("WAREHOUSE_ORDER_UNAVAILABLE");
        if(!readyLocked(id) && jdbc.queryForObject("SELECT COUNT(*) FROM delivery_jobs WHERE order_id=? AND status IN ('ACCEPTED','ON_THE_WAY','DELIVERED')",Integer.class,id)==0)
            throw new WarehouseException("WAREHOUSE_NOT_READY");
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireReadyLegacy(long deliveryId) {
        if(!enabled) return;
        var row=one("SELECT order_id FROM deliveries WHERE delivery_id=?",deliveryId);
        requireReadyOrder(((Number)row.get("order_id")).longValue());
    }
    private boolean readyLocked(long order) {
        var rows=jdbc.queryForList("SELECT status FROM warehouse_preparations WHERE order_id=? FOR UPDATE",order);
        return rows.size()==1 && "READY".equals(rows.get(0).get("status"));
    }
    public boolean canDeliver(long order) {
        if(!enabled) return true;
        return jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparations WHERE order_id=? AND status='READY'",Integer.class,order)==1;
    }
    /** Call in delivery mutation transaction: order first, then job. Shares lock order with packing. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireReadyJob(long job) {
        if(!enabled) return;
        long order=((Number)one("SELECT order_id FROM delivery_jobs WHERE id=?",job).get("order_id")).longValue();
        var o=one("SELECT payment_status,status FROM orders WHERE id=? FOR UPDATE",order);
        var j=one("SELECT status FROM delivery_jobs WHERE id=? FOR UPDATE",job);
        if(!"PAID".equals(o.get("payment_status")) || Set.of("CANCELLED","DELIVERED").contains(o.get("status")))
            throw new WarehouseException("WAREHOUSE_ORDER_UNAVAILABLE");
        // Already accepted routes are grandfathered when this feature is activated.
        if(!Set.of("ACCEPTED","ON_THE_WAY","DELIVERED").contains(j.get("status")) && !readyLocked(order))
            throw new WarehouseException("WAREHOUSE_NOT_READY");
    }
}
