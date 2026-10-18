package com.ninimum.api.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final JdbcTemplate jdbc;
    private static final String BALANCE = "COALESCE((SELECT SUM(i.invoice_amount) FROM supplier_invoices i JOIN warehouse_receipts r ON r.id=i.receipt_id WHERE i.supplier_id=s.id AND r.status='POSTED'),0)-COALESCE((SELECT SUM(p.amount) FROM supplier_payment_records p WHERE p.supplier_id=s.id AND p.status='RECORDED'),0)";

    public List<Map<String,Object>> list(String search) {
        String query="%"+WarehouseService.text(search,160,false)+"%";
        return jdbc.queryForList("SELECT s.*, "+BALANCE+" AS outstanding_amount FROM suppliers s WHERE s.name LIKE ? OR s.phone LIKE ? OR s.tax_id LIKE ? ORDER BY s.name,s.id LIMIT 500",query,query,query);
    }
    @Transactional public long save(Long id, Map<String,Object> body) {
        String name=WarehouseService.text(body.get("name"),160,true);
        String contact=WarehouseService.text(body.get("contact_name"),160,false);
        String phone=WarehouseService.text(body.get("phone"),40,false);
        String tax=WarehouseService.text(body.get("tax_id"),40,false);
        String address=WarehouseService.text(body.get("address"),500,false);
        String note=WarehouseService.text(body.get("note"),1000,false);
        int days;
        try { days=new BigDecimal(Objects.toString(body.getOrDefault("payment_term_days",0))).intValueExact(); }
        catch(Exception ex) { throw new WarehouseException("SUPPLIER_INVALID_TERMS"); }
        if(days<0||days>3650) throw new WarehouseException("SUPPLIER_INVALID_TERMS");
        Object activeValue=body.getOrDefault("is_active",true);
        if(!(activeValue instanceof Boolean)) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        boolean active=(Boolean)activeValue;
        if(id!=null) {
            if(jdbc.update("UPDATE suppliers SET name=?,contact_name=?,phone=?,tax_id=?,address=?,payment_term_days=?,note=?,is_active=?,updated_at=UTC_TIMESTAMP() WHERE id=?",name,contact,phone,tax,address,days,note,active,id)!=1)
                throw new WarehouseException("WAREHOUSE_NOT_FOUND");
            return id;
        }
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement("INSERT INTO suppliers(name,contact_name,phone,tax_id,address,payment_term_days,note,is_active,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",Statement.RETURN_GENERATED_KEYS);
            Object[] args={name,contact,phone,tax,address,days,note,active,WarehouseService.actor()};
            for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public Map<String,Object> detail(long id) {
        var rows=jdbc.queryForList("SELECT s.*, "+BALANCE+" AS outstanding_amount FROM suppliers s WHERE s.id=?",id);
        if(rows.isEmpty())throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        Map<String,Object> result=new LinkedHashMap<>(rows.get(0));
        result.put("invoices",jdbc.queryForList("SELECT i.receipt_id,r.invoice_number,r.status,i.invoice_amount,i.invoice_amount-COALESCE((SELECT SUM(p.amount) FROM supplier_payment_records p WHERE p.receipt_id=i.receipt_id AND p.status='RECORDED'),0) outstanding_amount,DATE_FORMAT(i.invoice_date,'%Y-%m-%d') invoice_date,DATE_FORMAT(i.due_date,'%Y-%m-%d') due_date,CASE WHEN r.status='POSTED' AND i.due_date<DATE(UTC_TIMESTAMP()+INTERVAL 5 HOUR) AND i.invoice_amount>COALESCE((SELECT SUM(p.amount) FROM supplier_payment_records p WHERE p.receipt_id=i.receipt_id AND p.status='RECORDED'),0) THEN 1 ELSE 0 END overdue FROM supplier_invoices i JOIN warehouse_receipts r ON r.id=i.receipt_id WHERE i.supplier_id=? ORDER BY i.due_date,i.receipt_id",id));
        result.put("payments",jdbc.queryForList("SELECT id,receipt_id,amount,reference,DATE_FORMAT(paid_date,'%Y-%m-%d') paid_date,status,created_by,voided_by,void_reason FROM supplier_payment_records WHERE supplier_id=? ORDER BY paid_date,id",id));
        return result;
    }
    public List<Map<String,Object>> calendar(String from,String to) {
        LocalDate start=invoiceDate(from),end=invoiceDate(to);
        if(end.isBefore(start)||end.isAfter(start.plusYears(1)))throw new WarehouseException("SUPPLIER_INVALID_DATE");
        return jdbc.queryForList("SELECT s.id supplier_id,s.name supplier_name,i.receipt_id,i.invoice_number,DATE_FORMAT(i.due_date,'%Y-%m-%d') due_date,i.invoice_amount-COALESCE((SELECT SUM(p.amount) FROM supplier_payment_records p WHERE p.receipt_id=i.receipt_id AND p.status='RECORDED'),0) outstanding_amount FROM supplier_invoices i JOIN suppliers s ON s.id=i.supplier_id JOIN warehouse_receipts r ON r.id=i.receipt_id WHERE r.status='POSTED' AND i.due_date BETWEEN ? AND ? HAVING outstanding_amount>0 ORDER BY i.due_date,s.name,i.receipt_id LIMIT 1000",start,end);
    }
    @Transactional public long recordPayment(long supplierId,Map<String,Object> body) {
        long receipt=WarehouseService.positive(body.get("receipt_id"));
        BigDecimal amount=invoiceAmount(body.get("amount"));
        String reference=WarehouseService.text(body.get("reference"),160,true);
        LocalDate date=invoiceDate(body.get("paid_date"));
        if(date.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Tashkent"))))throw new WarehouseException("SUPPLIER_INVALID_DATE");
        // Same lock order as receipt creation: supplier, then invoice/receipt.
        if(jdbc.queryForList("SELECT id FROM suppliers WHERE id=? FOR UPDATE",supplierId).isEmpty())throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        var prior=jdbc.queryForList("SELECT id,receipt_id,amount,paid_date FROM supplier_payment_records WHERE supplier_id=? AND reference=?",supplierId,reference);
        if(!prior.isEmpty()) {
            var p=prior.get(0);
            if(((Number)p.get("receipt_id")).longValue()!=receipt || new BigDecimal(p.get("amount").toString()).compareTo(amount)!=0 || !p.get("paid_date").toString().equals(date.toString()))throw new WarehouseException("SUPPLIER_REFERENCE_CONFLICT");
            return ((Number)p.get("id")).longValue();
        }
        var invoices=jdbc.queryForList("SELECT i.invoice_amount,r.status FROM supplier_invoices i JOIN warehouse_receipts r ON r.id=i.receipt_id WHERE i.supplier_id=? AND i.receipt_id=? FOR UPDATE",supplierId,receipt);
        if(invoices.isEmpty() || !"POSTED".equals(invoices.get(0).get("status")))throw new WarehouseException("WAREHOUSE_INVALID_STATE");
        BigDecimal paid=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM supplier_payment_records WHERE receipt_id=? AND status='RECORDED'",BigDecimal.class,receipt);
        BigDecimal total=new BigDecimal(invoices.get(0).get("invoice_amount").toString());
        if(amount.compareTo(total.subtract(paid))>0)throw new WarehouseException("SUPPLIER_OVERPAYMENT");
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement("INSERT INTO supplier_payment_records(supplier_id,receipt_id,amount,reference,paid_date,created_by,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP())",Statement.RETURN_GENERATED_KEYS);
            Object[] args={supplierId,receipt,amount,reference,date,WarehouseService.actor()};for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    @Transactional public void voidPayment(long supplierId,long paymentId,String reason) {
        reason=WarehouseService.text(reason,500,true);
        if(jdbc.queryForList("SELECT id FROM suppliers WHERE id=? FOR UPDATE",supplierId).isEmpty())throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        if(jdbc.queryForList("SELECT id FROM supplier_payment_records WHERE supplier_id=? AND id=? FOR UPDATE",supplierId,paymentId).isEmpty())throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        jdbc.update("UPDATE supplier_payment_records SET status='VOID',void_reason=?,voided_by=?,voided_at=UTC_TIMESTAMP() WHERE id=? AND supplier_id=? AND status='RECORDED'",reason,WarehouseService.actor(),paymentId,supplierId);
    }
    /** Called in the receipt transaction; amounts are recorded, never transferred. */
    public static BigDecimal invoiceAmount(Object value) {
        try {
            BigDecimal amount=new BigDecimal(String.valueOf(value)).setScale(2,RoundingMode.UNNECESSARY);
            if(amount.signum()<=0||amount.compareTo(new BigDecimal("99999999999999.99"))>0)throw new ArithmeticException();
            return amount;
        }catch(Exception ex){throw new WarehouseException("SUPPLIER_INVALID_AMOUNT");}
    }
    public static LocalDate invoiceDate(Object value) {
        try{return LocalDate.parse(String.valueOf(value));}
        catch(Exception ex){throw new WarehouseException("SUPPLIER_INVALID_DATE");}
    }
}
