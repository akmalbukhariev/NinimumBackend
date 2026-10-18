package com.ninimum.api.tariff.benefits;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ninimum.api.warehouse.WarehouseService;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TariffBenefitService {
    private final JdbcTemplate jdbc;
    public Map<String,Object> get(long id) {
        var result=new LinkedHashMap<>(jdbc.queryForMap("SELECT id,use_product_rules,free_delivery,recurring_order_days FROM tariff_plans WHERE id=?",id));
        result.put("products",jdbc.queryForList("SELECT b.product_id,p.name product_name,b.discount_percent FROM tariff_product_benefits b LEFT JOIN products p ON p.id=b.product_id WHERE b.tariff_id=? ORDER BY b.product_id",id));
        return result;
    }
    private static boolean flag(Object value){if(value instanceof Boolean)return (Boolean)value;if(value instanceof Number&&(((Number)value).intValue()==0||((Number)value).intValue()==1))return ((Number)value).intValue()==1;throw new IllegalArgumentException("Boolean required");}
    private static int integer(Object value,int min,int max){try{int n=new java.math.BigDecimal(value.toString()).intValueExact();if(n<min||n>max)throw new IllegalArgumentException();return n;}catch(Exception ex){throw new IllegalArgumentException("Invalid benefit value");}}
    @Transactional public void save(long id,Map<String,Object> body) throws Exception {
        jdbc.queryForObject("SELECT id FROM tariff_plans WHERE id=? FOR UPDATE",Long.class,id);
        Map<String,Object> before=get(id);
        boolean rules=flag(body.get("use_product_rules")),free=flag(body.get("free_delivery"));
        Integer days=body.get("recurring_order_days")==null||body.get("recurring_order_days").toString().isBlank()?null:integer(body.get("recurring_order_days"),1,365);
        if(!(body.get("products") instanceof List<?> products)||products.size()>500)throw new IllegalArgumentException("Product rules required (maximum 500)");
        var rows=new TreeMap<Long,Integer>();
        for(var value:products){if(!(value instanceof Map<?,?> item))throw new IllegalArgumentException("Invalid product rule");long product=integer(item.get("product_id"),1,Integer.MAX_VALUE);int percent=integer(item.get("discount_percent"),0,99);if(rows.put(product,percent)!=null)throw new IllegalArgumentException("Duplicate product rule");jdbc.queryForObject("SELECT id FROM products WHERE id=?",Long.class,product);}
        jdbc.update("UPDATE tariff_plans SET use_product_rules=?,free_delivery=?,recurring_order_days=? WHERE id=?",rules,free,days,id);
        jdbc.update("DELETE FROM tariff_product_benefits WHERE tariff_id=?",id);
        for(var row:rows.entrySet())jdbc.update("INSERT INTO tariff_product_benefits VALUES(?,?,?)",id,row.getKey(),row.getValue());
        var json=new ObjectMapper();jdbc.update("INSERT INTO tariff_benefit_history(tariff_id,changed_by,changed_at,before_json,after_json) VALUES(?,?,UTC_TIMESTAMP(),?,?)",id,WarehouseService.actor(),json.writeValueAsString(before),json.writeValueAsString(get(id)));
    }
    public static int discountedPrice(int regular,int percent){if(regular<=0||percent<0||percent>99)throw new IllegalArgumentException("Invalid price rule");return Math.max(1,java.math.BigDecimal.valueOf(regular).multiply(java.math.BigDecimal.valueOf(100-percent)).divide(java.math.BigDecimal.valueOf(100),0,java.math.RoundingMode.HALF_UP).intValueExact());}
}
