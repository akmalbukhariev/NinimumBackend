package com.ninimum.api.admin.management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ninimum.api.warehouse.WarehouseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomerCardService {
    private final JdbcTemplate jdbc;
    @Transactional(readOnly=true)
    public Map<String,Object> get(long id,String section,int page) {
        if(id<=0)throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        if(!Set.of("orders","payments","subscriptions","addresses").contains(section))throw new IllegalArgumentException("Invalid customer section");
        var profiles=jdbc.queryForList("SELECT id,first_name,last_name,phone_number,email,address,is_active,status,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%sZ') created_at FROM users WHERE id=?",id);
        if(profiles.isEmpty())throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        page=Math.max(1,page);long offset=((long)page-1)*20;
        String from,columns,sort;
        switch(section) {
            case "orders":
                from=" FROM orders WHERE user_id=?";
                columns="id,order_number,status,payment_status,total_price,delivery_address,DATE_FORMAT(ordered_at,'%Y-%m-%dT%H:%i:%sZ') ordered_at";
                sort="ordered_at DESC,id DESC";break;
            case "payments":
                from=" FROM payments WHERE user_id=?";
                columns="id,order_id,user_tariff_id,provider,provider_transaction_id,amount,status,payme_perform_time,payme_cancel_time";
                sort="id DESC";break;
            case "subscriptions":
                from=" FROM user_tariffs ut LEFT JOIN tariff_plans tp ON tp.id=ut.tariff_plan_id WHERE ut.user_id=?";
                columns="ut.id,tp.name tariff_name,ut.status,ut.auto_renew,DATE_FORMAT(ut.start_date,'%Y-%m-%d') start_date,DATE_FORMAT(ut.end_date,'%Y-%m-%d') end_date";
                sort="ut.id DESC";break;
            default:
                from=" FROM (SELECT TRIM(delivery_address) address,MAX(ordered_at) last_used,COUNT(*) order_count FROM orders WHERE user_id=? AND NULLIF(TRIM(delivery_address),'') IS NOT NULL GROUP BY TRIM(delivery_address)) a";
                columns="address,order_count,DATE_FORMAT(last_used,'%Y-%m-%dT%H:%i:%sZ') last_used";
                sort="last_used DESC,address";
        }
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,id));
        var items=jdbc.queryForList("SELECT "+columns+from+" ORDER BY "+sort+" LIMIT 20 OFFSET ?",id,offset);
        return Map.of("customer",profiles.get(0),"section",section,"items",items,"total",total,"page",page,"page_size",20);
    }
}
