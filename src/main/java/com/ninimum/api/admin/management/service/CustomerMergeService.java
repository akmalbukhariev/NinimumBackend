package com.ninimum.api.admin.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.common.PhoneNumbers;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** M11: one person with two accounts (the same phone written in different forms); the owner merges them. */
@Service
@RequiredArgsConstructor
public class CustomerMergeService {
    private final JdbcTemplate jdbc;
    /** Customer-owned tables; true where a unique key includes user_id, so a clashing row stays on the merged account. */
    private static final Map<String,Boolean> TABLES=new LinkedHashMap<>();
    static {
        TABLES.put("orders",false);TABLES.put("payments",false);TABLES.put("user_tariffs",false);TABLES.put("recurring_orders",true);
        TABLES.put("addresses",false);TABLES.put("user_addresses",false);TABLES.put("children",false);TABLES.put("cart_items",true);
        TABLES.put("favorites",true);TABLES.put("payment_cards",true);TABLES.put("reviews",false);TABLES.put("product_questions",false);
        TABLES.put("user_qr_codes",false);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> duplicates() {
        var groups=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var r:jdbc.queryForList("SELECT u.id,u.first_name,u.last_name,u.phone_number,u.status,DATE_FORMAT(u.created_at,'%Y-%m-%dT%H:%i:%sZ') created_at,"+
                "(SELECT COUNT(*) FROM orders o WHERE o.user_id=u.id) order_count FROM users u WHERE u.is_active=1 ORDER BY u.id")) {
            String e164=PhoneNumbers.normalize(Objects.toString(r.get("phone_number"),null));
            if(e164!=null)groups.computeIfAbsent(e164,k->new ArrayList<>()).add(r);
        }
        var out=new ArrayList<Map<String,Object>>();
        groups.forEach((phone,accounts)->{if(accounts.size()>1)out.add(Map.of("phone",phone,"accounts",accounts));});
        return out;
    }

    @Transactional
    public Map<String,Object> merge(long keepId,long mergeId,String reason) throws Exception {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||auth.getAuthorities().stream().noneMatch(a->Constant.ROLE_SUPER_ADMIN.equals(a.getAuthority())))
            throw new AccessDeniedException("Only the owner can merge customers");
        reason=reason==null?"":reason.trim();
        if(reason.isEmpty()||reason.length()>500)throw new WarehouseException("CUSTOMER_MERGE_REASON_REQUIRED");
        if(keepId<=0||mergeId<=0||keepId==mergeId)throw new WarehouseException("CUSTOMER_MERGE_INVALID");
        var rows=jdbc.queryForList("SELECT id,phone_number FROM users WHERE id IN (?,?) AND is_active=1 ORDER BY id FOR UPDATE",keepId,mergeId);
        if(rows.size()!=2)throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        var phones=new HashMap<Long,String>();
        for(var r:rows)phones.put(((Number)r.get("id")).longValue(),Objects.toString(r.get("phone_number"),""));
        String e164=PhoneNumbers.normalize(phones.get(keepId));
        if(e164==null||!e164.equals(PhoneNumbers.normalize(phones.get(mergeId))))throw new WarehouseException("CUSTOMER_MERGE_PHONE_MISMATCH");
        var present=new HashSet<>(jdbc.queryForList("SELECT table_name FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='user_id'",String.class));
        var moved=new LinkedHashMap<String,Integer>();
        TABLES.forEach((table,unique)->{
            if(!present.contains(table))return;
            // Counted before and after: the driver reports matched (not changed) rows, and IGNORE skips clashing rows.
            String count="SELECT COUNT(*) FROM "+table+" WHERE user_id=?";
            int before=Objects.requireNonNull(jdbc.queryForObject(count,Integer.class,mergeId));
            jdbc.update("UPDATE "+(unique?"IGNORE ":"")+table+" SET user_id=? WHERE user_id=?",keepId,mergeId);
            moved.put(table,before-Objects.requireNonNull(jdbc.queryForObject(count,Integer.class,mergeId)));
        });
        // The merged account keeps its row (history), frees its number and can no longer sign in.
        jdbc.update("UPDATE users SET phone_number=?,is_active=0,status='DELETED',updated_at=NOW() WHERE id=?","merged-"+mergeId,mergeId);
        if(Integer.valueOf(0).equals(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE phone_number=? AND id<>?",Integer.class,e164,keepId)))
            jdbc.update("UPDATE users SET phone_number=?,updated_at=NOW() WHERE id=?",e164,keepId);
        String movedJson=new ObjectMapper().writeValueAsString(moved);
        jdbc.update("INSERT INTO customer_merges(kept_user_id,merged_user_id,merged_phone,moved_rows,reason,merged_by,merged_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP())",
                keepId,mergeId,phones.get(mergeId),movedJson,reason,WarehouseService.actor());
        return Map.of("kept_user_id",keepId,"merged_user_id",mergeId,"phone",e164,"moved",moved);
    }
}
