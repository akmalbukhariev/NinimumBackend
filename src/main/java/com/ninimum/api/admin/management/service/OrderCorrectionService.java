package com.ninimum.api.admin.management.service;

import com.ninimum.api.audit.OrderAuditActor;
import com.ninimum.api.constants.Constant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderCorrectionService {
    private final JdbcTemplate jdbc;
    private static final List<String> STAGES=List.of("CONFIRMED","PREPARING","READY","ON_THE_WAY","DELIVERED");

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void correct(long id, Map<String,Object> body) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->Constant.ROLE_SUPER_ADMIN.equals(a.getAuthority())))
            throw new AccessDeniedException("Only the owner can correct an order status");
        String target=Objects.toString(body.get("status"),"");
        String expected=Objects.toString(body.get("expected_status"),"");
        String reason=Objects.toString(body.get("reason"),"").trim();
        if(reason.isEmpty() || reason.length()>500) throw new IllegalArgumentException("A reason of 1–500 characters is required");
        var rows=jdbc.queryForList("SELECT status,payment_status FROM orders WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Order not found");
        String current=Objects.toString(rows.get(0).get("status"),"");
        if(!expected.equals(current)) throw new IllegalArgumentException("The order changed. Refresh it and try again");
        if(!"PAID".equals(rows.get(0).get("payment_status")) || STAGES.indexOf(target)<0 || STAGES.indexOf(current)<=STAGES.indexOf(target))
            throw new IllegalArgumentException("Only a paid order can be moved to an earlier operational stage");
        var jobs=jdbc.queryForList("SELECT id,status,delivery_worker_id FROM delivery_jobs WHERE order_id=? FOR UPDATE",id);
        var packing=jdbc.queryForList("SELECT status,worker_code FROM warehouse_preparations WHERE order_id=? FOR UPDATE",id);
        if(jobs.stream().anyMatch(j->"CANCELLED".equals(j.get("status"))))
            throw new IllegalArgumentException("A cancelled delivery cannot be reopened here");
        if("PREPARING".equals(target)) {
            if(packing.isEmpty() || packing.get(0).get("worker_code")==null ||
               jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_workers WHERE worker_code=? AND status='ACTIVE'",Integer.class,packing.get(0).get("worker_code"))!=1)
                throw new IllegalArgumentException("Restart at Confirmed so an active warehouse worker can claim the order");
        }
        if(List.of("READY","ON_THE_WAY").contains(target)) {
            if(packing.isEmpty() || !"READY".equals(packing.get(0).get("status")) ||
               jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_items WHERE order_id=?",Integer.class,id)==0 ||
               jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_items WHERE order_id=? AND checked_quantity<>required_quantity",Integer.class,id)!=0)
                throw new IllegalArgumentException("The warehouse must have checked and packed the order");
        }
        if("ON_THE_WAY".equals(target)) {
            if(jobs.size()!=1 || jobs.get(0).get("delivery_worker_id")==null ||
               jdbc.queryForObject("SELECT COUNT(*) FROM delivery_app_workers WHERE id=? AND status='ACTIVE'",Integer.class,jobs.get(0).get("delivery_worker_id"))!=1)
                throw new IllegalArgumentException("An active assigned courier is required");
            jdbc.update("UPDATE delivery_jobs SET status='ON_THE_WAY',delivered_at=NULL,updated_at=NOW() WHERE order_id=?",id);
        } else {
            jdbc.update("UPDATE delivery_jobs SET status='WAITING_ASSIGNMENT',delivery_worker_id=NULL,accepted_at=NULL,on_the_way_at=NULL,delivered_at=NULL,note=NULL,updated_at=NOW() WHERE order_id=?",id);
        }
        if("CONFIRMED".equals(target)) {
            jdbc.update("DELETE FROM warehouse_preparation_items WHERE order_id=?",id);
            jdbc.update("UPDATE warehouse_preparations SET status='WAITING',worker_code=NULL,note=NULL,started_at=NULL,ready_at=NULL,updated_at=UTC_TIMESTAMP() WHERE order_id=?",id);
        } else if("PREPARING".equals(target)) {
            jdbc.update("UPDATE warehouse_preparation_items SET checked_quantity=0 WHERE order_id=?",id);
            jdbc.update("UPDATE warehouse_preparations SET status='PICKING',note=NULL,ready_at=NULL,updated_at=UTC_TIMESTAMP() WHERE order_id=?",id);
        }
        if(!packing.isEmpty()) jdbc.update("INSERT INTO warehouse_preparation_events(order_id,actor,kind,note,created_at) VALUES (?,?, 'OWNER_CORRECTION',?,UTC_TIMESTAMP())",id,auth.getName(),reason);
        jdbc.update("UPDATE orders SET status=?,delivered_at=NULL,status_actor=?,status_source='OWNER_CORRECTION',status_reason=?,updated_at=NOW() WHERE id=?",target,OrderAuditActor.current(),reason,id);
    }
}
