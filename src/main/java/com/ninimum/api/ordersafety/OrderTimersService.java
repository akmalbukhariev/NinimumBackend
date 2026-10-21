package com.ninimum.api.ordersafety;

import com.ninimum.api.audit.OrderAuditActor;
import com.ninimum.api.constants.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Slf4j
@Service
public class OrderTimersService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private boolean schemaWarning;
    private static final ZoneId TASHKENT=ZoneId.of("Asia/Tashkent");
    @org.springframework.beans.factory.annotation.Value("${warehouse.enabled:false}") private boolean warehouseEnabled;
    @org.springframework.beans.factory.annotation.Value("${warehouse.inventory-enabled:false}") private boolean inventoryEnabled;
    public OrderTimersService(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public Map<String,Object> settings() { return jdbc.queryForMap("SELECT * FROM order_timer_settings WHERE id=1"); }
    public void saveSettings(Map<String,Object> body) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->Constant.ROLE_SUPER_ADMIN.equals(a.getAuthority())))
            throw new AccessDeniedException("Only the owner can change timers");
        var value=new LinkedHashMap<>(body);
        for(String key:List.of("unpaid_minutes","queue_minutes","preparation_minutes","courier_wait_minutes","delivery_minutes","question_minutes")) {
            int n=integer(body.get(key));if(n<1 || n>43200) throw new IllegalArgumentException("Timer must be 1–43200 minutes");value.put(key,n);
        }
        int complaint=integer(body.containsKey("complaint_minutes")?body.get("complaint_minutes"):settings().get("complaint_minutes"));
        if(complaint<1||complaint>43200)throw new IllegalArgumentException("Invalid complaint timer");
        value.put("complaint_minutes",complaint);
        int enabled=integer(body.get("expiry_enabled"));if(enabled!=0 && enabled!=1) throw new IllegalArgumentException("Invalid expiry setting");
        value.put("working_days",integer(body.get("working_days")));new WorkingHours(value);
        transaction.executeWithoutResult(tx->{
            jdbc.queryForMap("SELECT id FROM order_timer_settings WHERE id=1 FOR UPDATE");
            jdbc.update("UPDATE order_timer_settings SET expiry_enabled=?,unpaid_minutes=?,queue_minutes=?,preparation_minutes=?,courier_wait_minutes=?,delivery_minutes=?,question_minutes=?,complaint_minutes=?,working_days=?,working_start=?,working_end=?,updated_by=?,updated_at=UTC_TIMESTAMP() WHERE id=1",
                enabled,value.get("unpaid_minutes"),value.get("queue_minutes"),value.get("preparation_minutes"),value.get("courier_wait_minutes"),value.get("delivery_minutes"),value.get("question_minutes"),value.get("complaint_minutes"),value.get("working_days"),value.get("working_start"),value.get("working_end"),OrderAuditActor.current());
            event("SETTINGS",1,"TIMERS","UPDATED",OrderAuditActor.current(),Instant.now());
        });
    }
    private static int integer(Object value) {
        try { return new java.math.BigDecimal(String.valueOf(value)).intValueExact(); }
        catch(Exception ex) {throw new IllegalArgumentException("Invalid number");}
    }
    @Scheduled(fixedDelayString="${orders.timers.interval-ms:60000}",initialDelayString="${orders.timers.initial-delay-ms:60000}")
    public synchronized void tick() {
        try {scan(Instant.now());schemaWarning=false;}
        catch(org.springframework.dao.DataAccessException ex) {
            if(!schemaWarning) log.warn("Order timers unavailable. Check database migration and connectivity.");schemaWarning=true;
        }
    }
    public void scan(Instant now) {
        transaction.executeWithoutResult(tx->{
            // A single row lock serializes workers across backend instances and settings edits.
            var config=jdbc.queryForMap("SELECT * FROM order_timer_settings WHERE id=1 FOR UPDATE");
            var hours=new WorkingHours(config);
            if(integer(config.get("expiry_enabled"))==1) expire(config,hours,now);
            var active=new HashSet<String>();
            var orders=jdbc.queryForList("SELECT o.id,o.status,o.payment_status,DATE_FORMAT(o.ordered_at,'%Y-%m-%dT%H:%i:%sZ') ordered_at,"+
                "DATE_FORMAT((SELECT h.changed_at FROM order_status_history h WHERE h.order_id=o.id AND h.to_status=o.status ORDER BY h.id DESC LIMIT 1),'%Y-%m-%dT%H:%i:%sZ') state_since,"+
                "wp.worker_code,dj.delivery_worker_id,COALESCE(dw.full_name,dw.worker_code,CAST(dj.delivery_worker_id AS CHAR)) courier_name FROM orders o LEFT JOIN warehouse_preparations wp ON wp.order_id=o.id LEFT JOIN delivery_jobs dj ON dj.order_id=o.id LEFT JOIN delivery_app_workers dw ON dw.id=dj.delivery_worker_id "+
                "WHERE o.payment_status='PAID' AND o.status IN ('PENDING','CONFIRMED','PREPARING','READY','ON_THE_WAY','RETURNING','RETURNED','CANCELLED')");
            for(var order:orders) {
                long id=((Number)order.get("id")).longValue();String status=String.valueOf(order.get("status"));
                Instant since=instant(order.get("state_since"));if(since==null) since=instant(order.get("ordered_at"));if(since==null)continue;
                String kind,key,responsible;
                switch(status) {
                    case "PENDING":case "CONFIRMED":kind="PREPARATION_QUEUE";key="queue_minutes";responsible="WAREHOUSE";break;
                    case "PREPARING":kind="PREPARATION_DELAY";key="preparation_minutes";responsible=Objects.toString(order.get("worker_code"),"WAREHOUSE");break;
                    case "READY":kind=order.get("delivery_worker_id")==null?"COURIER_WAIT":"COURIER_PICKUP";key="courier_wait_minutes";responsible=Objects.toString(order.get("courier_name"),"DISPATCHER");break;
                    case "ON_THE_WAY":kind="DELIVERY_DELAY";key="delivery_minutes";responsible=Objects.toString(order.get("courier_name"),"DISPATCHER");break;
                    default:
                        kind="REFUND_PENDING";key=null;responsible="OWNER";
                        // Keep the refund clock across warehouse receipt; do not restart it at RETURNED.
                        var rows=jdbc.queryForList("SELECT DATE_FORMAT(MIN(h.changed_at),'%Y-%m-%dT%H:%i:%sZ') since FROM order_status_history h WHERE h.order_id=? AND h.to_status IN ('RETURNING','RETURNED','CANCELLED') AND h.id>COALESCE((SELECT MAX(n.id) FROM order_status_history n WHERE n.order_id=? AND n.to_status NOT IN ('RETURNING','RETURNED','CANCELLED')),0)",id,id);
                        Instant refundSince=instant(rows.get(0).get("since"));if(refundSince!=null) since=refundSince;
                }
                long elapsed=key==null?Math.max(0,Duration.between(since,now).toMinutes()):hours.minutes(since,now);
                // A paid cancelled/returned order stays problematic until its refund is confirmed.
                if(key==null || elapsed>=integer(config.get(key))) open("ORDER",id,kind,status,responsible,since,elapsed,now,active);
            }
            for(var partial:jdbc.queryForList("SELECT pr.order_id,DATE_FORMAT(pr.created_at,'%Y-%m-%dT%H:%i:%sZ') since FROM delivery_partial_returns pr JOIN orders o ON o.id=pr.order_id WHERE pr.refund_status='PENDING' AND pr.refund_amount>0 AND o.payment_status='PAID'")) {
                Instant since=instant(partial.get("since"));
                if(since!=null) open("ORDER",((Number)partial.get("order_id")).longValue(),"REFUND_PENDING","DELIVERED","OWNER",since,Math.max(0,Duration.between(since,now).toMinutes()),now,active);
            }
            for(var question:jdbc.queryForList("SELECT id,kind,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%sZ') since FROM product_questions WHERE is_active=1 AND status<>'ANSWERED' AND (answer IS NULL OR TRIM(answer)='')")) {
                Instant since=instant(question.get("since"));if(since==null) continue;long elapsed=hours.minutes(since,now);
                if(elapsed>=QuestionResponsePolicy.minutes(config,String.valueOf(question.get("kind")))) open("QUESTION",((Number)question.get("id")).longValue(),"UNANSWERED_QUESTION",null,"SUPPORT",since,elapsed,now,active);
            }
            for(var offer:jdbc.queryForList("SELECT j.order_id,DATE_FORMAT(f.updated_at,'%Y-%m-%dT%H:%i:%sZ') since FROM courier_dispatch_offers f JOIN delivery_jobs j ON j.id=f.job_id JOIN orders o ON o.id=j.order_id WHERE f.status='ESCALATED' AND j.delivery_worker_id IS NULL AND o.payment_status='PAID' AND o.status='READY'")) {
                Instant since=instant(offer.get("since"));if(since!=null)open("ORDER",((Number)offer.get("order_id")).longValue(),"COURIER_REASSIGN","READY","DISPATCHER",since,Math.max(0,Duration.between(since,now).toMinutes()),now,active);
            }
            if(warehouseEnabled&&inventoryEnabled) batchExpiry(now,active);
            for(var old:jdbc.queryForList("SELECT entity_type,entity_id,kind FROM order_problem_alerts WHERE resolved_at IS NULL")) {
                String type=String.valueOf(old.get("entity_type")),kind=String.valueOf(old.get("kind"));long id=((Number)old.get("entity_id")).longValue();
                if(!active.contains(type+":"+id+":"+kind)) {
                    jdbc.update("UPDATE order_problem_alerts SET resolved_at=? WHERE entity_type=? AND entity_id=? AND kind=?",timestamp(now),type,id,kind);
                    event(type,id,kind,"RESOLVED","SYSTEM",now);
                }
            }
            jdbc.update("UPDATE order_timer_settings SET last_scanned_at=? WHERE id=1",timestamp(now));
        });
    }
    /** Inventory: a batch with stock that is past its expiry date, or expires within the warning period (Tashkent dates). */
    private void batchExpiry(Instant now,Set<String> active) {
        int warning=integer(jdbc.queryForObject("SELECT expiry_warning_days FROM inventory_settings WHERE id=1",Integer.class));
        for(var batch:jdbc.queryForList("SELECT b.id,DATE_FORMAT(b.expiry_date,'%Y-%m-%d') expiry_date,DATEDIFF(b.expiry_date,DATE(CONVERT_TZ(?,'+00:00','+05:00'))) days_left "+
                "FROM inventory_batches b JOIN inventory_products i ON i.product_id=b.product_id WHERE b.physical_quantity>0 AND b.expiry_date IS NOT NULL "+
                "AND b.expiry_date<=DATE(CONVERT_TZ(?,'+00:00','+05:00'))+INTERVAL ? DAY",timestamp(now),timestamp(now),warning)) {
            // Expired from the expiry date itself, as BatchStock stops selling it; expiring from the start of the warning period.
            boolean expired=((Number)batch.get("days_left")).longValue()<=0;
            Instant since=LocalDate.parse(String.valueOf(batch.get("expiry_date"))).minusDays(expired?0:warning).atStartOfDay(TASHKENT).toInstant();
            open("BATCH",((Number)batch.get("id")).longValue(),expired?"STOCK_EXPIRED":"STOCK_EXPIRING",null,"WAREHOUSE",since,Math.max(0,Duration.between(since,now).toMinutes()),now,active);
        }
    }
    private void expire(Map<String,Object> config,WorkingHours hours,Instant now) {
        for(var row:jdbc.queryForList("SELECT id,DATE_FORMAT(ordered_at,'%Y-%m-%dT%H:%i:%sZ') since FROM orders WHERE status='PENDING' AND payment_status='PENDING' ORDER BY id")) {
            long id=((Number)row.get("id")).longValue();Instant since=instant(row.get("since"));
            if(since==null || hours.minutes(since,now)<integer(config.get("unpaid_minutes")))continue;
            // Payme callbacks lock payment then order. Use the same order to serialize expiry/payment races.
            var payments=jdbc.queryForList("SELECT id,status FROM payments WHERE order_id=? ORDER BY id FOR UPDATE",id);
            var order=jdbc.queryForMap("SELECT status,payment_status FROM orders WHERE id=? FOR UPDATE",id);
            if(!"PENDING".equals(order.get("status")) || !"PENDING".equals(order.get("payment_status")) || payments.stream().anyMatch(p->"PAID".equals(p.get("status"))))continue;
            int changed=jdbc.update("UPDATE orders SET status='CANCELLED',status_actor='SYSTEM',status_source='AUTO_EXPIRE',status_reason='UNPAID_PAYMENT_TIMEOUT',cancel_reason='To‘lov muddati tugadi',cancelled_at=?,updated_at=? WHERE id=? AND status='PENDING' AND payment_status='PENDING'",timestamp(now),timestamp(now),id);
            if(changed==1) {
                jdbc.update("UPDATE delivery_jobs SET status='CANCELLED',updated_at=? WHERE order_id=?",timestamp(now),id);
                // Preparation screens use the canonical order status; no stock was deducted for an unpaid order.
                event("ORDER",id,"UNPAID_EXPIRED","EXPIRED","SYSTEM",now);
            }
        }
    }
    private void open(String type,long id,String kind,String status,String responsible,Instant since,long elapsed,Instant now,Set<String> active) {
        active.add(type+":"+id+":"+kind);
        var previous=jdbc.queryForList("SELECT resolved_at,DATE_FORMAT(state_since,'%Y-%m-%dT%H:%i:%sZ') since FROM order_problem_alerts WHERE entity_type=? AND entity_id=? AND kind=?",type,id,kind);
        boolean episode=previous.isEmpty() || previous.get(0).get("resolved_at")!=null || !since.equals(instant(previous.get(0).get("since")));
        if(episode && !previous.isEmpty() && previous.get(0).get("resolved_at")==null)event(type,id,kind,"RESOLVED","SYSTEM",now);
        jdbc.update("INSERT INTO order_problem_alerts(entity_type,entity_id,kind,order_status,responsible,state_since,elapsed_minutes,opened_at,resolved_at) VALUES(?,?,?,?,?,?,?,?,NULL) ON DUPLICATE KEY UPDATE order_status=VALUES(order_status),responsible=VALUES(responsible),state_since=VALUES(state_since),elapsed_minutes=VALUES(elapsed_minutes),opened_at=IF(?=1,VALUES(opened_at),opened_at),resolved_at=NULL",type,id,kind,status,responsible,timestamp(since),elapsed,timestamp(now),episode?1:0);
        if(episode)event(type,id,kind,"OPENED","SYSTEM",now);
    }
    private void event(String type,long id,String kind,String action,String actor,Instant now) {
        jdbc.update("INSERT INTO order_problem_events(entity_type,entity_id,kind,event,occurred_at,actor) VALUES(?,?,?,?,?,?)",type,id,kind,action,timestamp(now),actor);
    }
    public Map<String,Object> problems(int page,int size,boolean history) {
        size=Math.min(100,Math.max(1,size));page=Math.max(1,page);
        var result=new LinkedHashMap<String,Object>();
        String where=" WHERE 1=1"+(history?"":" AND resolved_at IS NULL")+(owner()?"":" AND entity_type='ORDER'");
        result.put("total",jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts"+where,Integer.class));
        var items=jdbc.queryForList("SELECT entity_type,entity_id,kind,order_status,responsible,elapsed_minutes,DATE_FORMAT(state_since,'%Y-%m-%dT%H:%i:%sZ') state_since,DATE_FORMAT(opened_at,'%Y-%m-%dT%H:%i:%sZ') opened_at,DATE_FORMAT(resolved_at,'%Y-%m-%dT%H:%i:%sZ') resolved_at FROM order_problem_alerts"+where+" ORDER BY resolved_at IS NULL DESC,opened_at DESC,entity_id DESC LIMIT ? OFFSET ?",size,(page-1)*size);
        batchDetails(items);
        result.put("items",items);
        result.put("page",page);result.put("page_size",size);return result;
    }
    /** Product, batch, bin and stock for batch alerts, so the list can say what to remove or sell first. */
    private void batchDetails(List<Map<String,Object>> items) {
        var ids=items.stream().filter(r->"BATCH".equals(r.get("entity_type"))).map(r->((Number)r.get("entity_id")).longValue()).distinct().toList();
        if(ids.isEmpty())return;
        var details=new HashMap<Long,Map<String,Object>>();
        for(var d:jdbc.queryForList("SELECT b.id,p.name product_name,b.batch_number,DATE_FORMAT(b.expiry_date,'%Y-%m-%d') expiry_date,b.physical_quantity,l.code location_code FROM inventory_batches b "+
                "JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id WHERE b.id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",ids.toArray()))
            details.put(((Number)d.get("id")).longValue(),d);
        for(var r:items) {
            var d="BATCH".equals(r.get("entity_type"))?details.get(((Number)r.get("entity_id")).longValue()):null;
            if(d!=null)for(String key:List.of("product_name","batch_number","expiry_date","physical_quantity","location_code"))r.put(key,d.get(key));
        }
    }
    public Map<String,Object> summary() {
        var result=new LinkedHashMap<String,Object>();
        result.put("total",jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts WHERE resolved_at IS NULL"+(owner()?"":" AND entity_type='ORDER'"),Integer.class));
        result.put("last_scan",Objects.toString(jdbc.queryForObject("SELECT DATE_FORMAT(last_scanned_at,'%Y-%m-%dT%H:%i:%sZ') FROM order_timer_settings WHERE id=1",String.class),""));
        if(owner())for(String kind:List.of("STOCK_EXPIRED","STOCK_EXPIRING"))
            result.put(kind.toLowerCase(),jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts WHERE resolved_at IS NULL AND entity_type='BATCH' AND kind=?",Integer.class,kind));
        return result;
    }
    private static boolean owner() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.getAuthorities().stream().anyMatch(a->Constant.ROLE_SUPER_ADMIN.equals(a.getAuthority()));
    }
    public Map<String,Object> events(int page,int size) {
        size=Math.min(100,Math.max(1,size));page=Math.max(1,page);
        String where=owner()?"":" WHERE entity_type='ORDER'";
        return Map.of("total",jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events"+where,Integer.class),
          "items",jdbc.queryForList("SELECT entity_type,entity_id,kind,event,actor,DATE_FORMAT(occurred_at,'%Y-%m-%dT%H:%i:%sZ') occurred_at FROM order_problem_events"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",size,(page-1)*size),"page",page,"page_size",size);
    }
    static Instant instant(Object value) {return value==null?null:Instant.parse(String.valueOf(value));}
    static java.sql.Timestamp timestamp(Instant value) {return java.sql.Timestamp.valueOf(LocalDateTime.ofInstant(value,ZoneOffset.UTC));}
}
