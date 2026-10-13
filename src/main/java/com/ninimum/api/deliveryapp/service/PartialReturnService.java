package com.ninimum.api.deliveryapp.service;

import com.ninimum.api.param.DeliveryAppStatusParam;
import com.ninimum.api.param.ReturnQuantityParam;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PartialReturnService {
    private final JdbcTemplate jdbc;
    private final ReturnPhotoService photos;

    public Map<String,Object> details(long orderId) {
        var rows=jdbc.queryForList("SELECT status,reason,refund_amount,refund_status,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%sZ') created_at FROM delivery_partial_returns WHERE order_id=?",orderId);
        if(rows.isEmpty()) return null;
        var result=new LinkedHashMap<>(rows.get(0));
        result.put("items",jdbc.queryForList("SELECT product_id,product_name,quantity,refund_amount FROM delivery_partial_return_items WHERE order_id=? ORDER BY product_id",orderId));
        return result;
    }

    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void start(long workerId,String actor,DeliveryAppStatusParam request) throws Exception {
        String reason=Objects.toString(request.getNote(),"").trim();
        if(reason.isEmpty() || reason.length()>500) throw new IllegalArgumentException("RETURN_REASON_REQUIRED");
        var order=jdbc.queryForMap("SELECT o.id,o.status,o.payment_status,o.total_price,o.delivery_price,j.status job_status FROM orders o JOIN delivery_jobs j ON j.order_id=o.id WHERE j.id=? AND j.delivery_worker_id=? FOR UPDATE",request.getJobId(),workerId);
        long id=((Number)order.get("id")).longValue();
        var previous=jdbc.queryForList("SELECT reason FROM delivery_partial_returns WHERE order_id=?",id);
        if(!previous.isEmpty()) {
            var selected=quantities(request.getReturnItems());
            var saved=new TreeMap<Long,Integer>();
            for(var line:jdbc.queryForList("SELECT product_id,quantity FROM delivery_partial_return_items WHERE order_id=?",id))
                saved.put(((Number)line.get("product_id")).longValue(),((Number)line.get("quantity")).intValue());
            if(selected.equals(saved) && reason.equals(previous.get(0).get("reason")) && photos.matches(request.getJobId(),reason,request.getPhotoBase64())) return;
            throw new IllegalArgumentException("PARTIAL_RETURN_ALREADY_SAVED");
        }
        if(!"ON_THE_WAY".equals(order.get("job_status")) || !"ON_THE_WAY".equals(order.get("status")) || !"PAID".equals(order.get("payment_status")))
            throw new IllegalArgumentException("PARTIAL_RETURN_UNAVAILABLE");
        var selected=quantities(request.getReturnItems());
        var lines=jdbc.queryForList("SELECT product_id,MAX(product_name) product_name,SUM(quantity) quantity,SUM(total_price) amount FROM order_items WHERE order_id=? GROUP BY product_id ORDER BY product_id",id);
        long bought=0,returned=selected.values().stream().mapToLong(Integer::longValue).sum();
        BigDecimal gross=BigDecimal.ZERO;
        var purchased=new HashMap<Long,Map<String,Object>>();
        for(var line:lines) {
            long product=((Number)line.get("product_id")).longValue();
            long quantity=((Number)line.get("quantity")).longValue();
            BigDecimal amount=new BigDecimal(line.get("amount").toString());
            if(quantity<1 || amount.signum()<0) throw new IllegalArgumentException("PARTIAL_RETURN_INVALID");
            bought+=quantity;gross=gross.add(amount);purchased.put(product,line);
        }
        if(returned<1 || returned>=bought) throw new IllegalArgumentException("PARTIAL_RETURN_INVALID");
        BigDecimal paidGoods=new BigDecimal(order.get("total_price").toString()).subtract(new BigDecimal(Objects.toString(order.get("delivery_price"),"0")));
        if(paidGoods.signum()<0 || paidGoods.compareTo(gross)>0) throw new IllegalArgumentException("PARTIAL_RETURN_INVALID");
        var amounts=new TreeMap<Long,BigDecimal>();
        BigDecimal total=BigDecimal.ZERO;
        for(var entry:selected.entrySet()) {
            var line=purchased.get(entry.getKey());
            if(line==null || entry.getValue()>((Number)line.get("quantity")).longValue()) throw new IllegalArgumentException("PARTIAL_RETURN_INVALID");
            BigDecimal amount=gross.signum()==0?BigDecimal.ZERO:rawRefund(paidGoods,gross,new BigDecimal(line.get("amount").toString()),((Number)line.get("quantity")).longValue(),entry.getValue());
            amounts.put(entry.getKey(),amount);total=total.add(amount);
        }
        // Round the total once, then allocate minor-unit remainders deterministically.
        total=total.setScale(2,RoundingMode.HALF_UP);
        var raw=new TreeMap<>(amounts);BigDecimal floorTotal=BigDecimal.ZERO;
        for(var entry:amounts.entrySet()) { entry.setValue(entry.getValue().setScale(2,RoundingMode.DOWN));floorTotal=floorTotal.add(entry.getValue()); }
        int remainder=total.subtract(floorTotal).movePointRight(2).intValueExact();
        var allocation=new ArrayList<>(amounts.keySet());
        allocation.sort(Comparator.<Long,BigDecimal>comparing(product->raw.get(product).subtract(amounts.get(product))).reversed().thenComparingLong(Long::longValue));
        for(int i=0;i<remainder;i++) { long product=allocation.get(i);amounts.put(product,amounts.get(product).add(new BigDecimal("0.01"))); }
        if(total.compareTo(paidGoods)>0) throw new IllegalArgumentException("PARTIAL_RETURN_INVALID");
        jdbc.update("INSERT INTO delivery_partial_returns(order_id,delivery_job_id,reason,actor,refund_amount,refund_status,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP())",id,request.getJobId(),reason,actor,total,total.signum()==0?"NOT_REQUIRED":"PENDING");
        for(var entry:selected.entrySet()) jdbc.update("INSERT INTO delivery_partial_return_items(order_id,product_id,product_name,quantity,refund_amount) VALUES(?,?,?,?,?)",id,entry.getKey(),purchased.get(entry.getKey()).get("product_name"),entry.getValue(),amounts.get(entry.getKey()));
        if(jdbc.update("UPDATE delivery_jobs SET status='DELIVERED',note=?,delivered_at=NOW(),updated_at=NOW() WHERE id=? AND delivery_worker_id=? AND status='ON_THE_WAY'",reason,request.getJobId(),workerId)!=1)
            throw new IllegalArgumentException("PARTIAL_RETURN_UNAVAILABLE");
        photos.save(request.getJobId(),actor,reason,request.getPhotoBase64());
        if(jdbc.update("UPDATE orders SET status='DELIVERED',delivered_at=NOW(),status_actor=?,status_source='COURIER',status_reason=?,updated_at=NOW() WHERE id=? AND status='ON_THE_WAY' AND payment_status='PAID'",actor,reason,id)!=1)
            throw new IllegalArgumentException("PARTIAL_RETURN_UNAVAILABLE");
        jdbc.update("INSERT INTO delivery_job_tracking(delivery_job_id,delivery_worker_id,status,message,created_at) VALUES(?,?,'DELIVERED',?,NOW())",request.getJobId(),workerId,"Partial return: "+reason);
    }

    static Map<Long,Integer> quantities(List<ReturnQuantityParam> items) {
        if(items==null || items.isEmpty() || items.size()>1000) throw new IllegalArgumentException("PARTIAL_RETURN_INVALID");
        var result=new TreeMap<Long,Integer>();
        for(var item:items) if(item==null || item.getProductId()==null || item.getProductId()<1 || item.getQuantity()==null || item.getQuantity()<1 || result.putIfAbsent(item.getProductId(),item.getQuantity())!=null)
            throw new IllegalArgumentException("PARTIAL_RETURN_INVALID");
        return result;
    }
    static BigDecimal refund(BigDecimal paidGoods,BigDecimal gross,BigDecimal lineGross,long purchased,int returned) {
        return rawRefund(paidGoods,gross,lineGross,purchased,returned).setScale(2,RoundingMode.HALF_UP);
    }
    private static BigDecimal rawRefund(BigDecimal paidGoods,BigDecimal gross,BigDecimal lineGross,long purchased,int returned) {
        return paidGoods.multiply(lineGross).multiply(BigDecimal.valueOf(returned))
                .divide(gross.multiply(BigDecimal.valueOf(purchased)),20,RoundingMode.HALF_UP);
    }
}
