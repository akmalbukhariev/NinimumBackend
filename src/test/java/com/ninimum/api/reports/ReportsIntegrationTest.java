package com.ninimum.api.reports;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TZ v10 §11 reports on the real production schema. Needs the isolated local reports_test DB built from
 * audit/schema/ninimum_schema_2026-10-17.sql plus every database/migrations file from 20261017 on.
 */
@EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*")
class ReportsIntegrationTest {
    JdbcTemplate jdbc;ReportsService reports;
    LocalDate today=ReportsService.today();
    String from=today.minusDays(10).toString(),to=today.toString();

    @BeforeEach void setup(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        // One connection, so the foreign-key switch below stays in effect for the sample data.
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("order_status_history","delivery_partial_return_items","delivery_partial_returns","order_items","orders","warehouse_stock_movements","inventory_batches","inventory_documents","inventory_products",
                "warehouse_receipt_counts","warehouse_receipt_items","supplier_payment_records","supplier_invoices","warehouse_receipts","suppliers","warehouse_locations","products","categories","delivery_jobs","delivery_app_workers","user_tariffs","tariff_plans","payments"))jdbc.execute("DELETE FROM "+t);
        reports=new ReportsService(jdbc);
        jdbc.update("INSERT INTO categories(id,name) VALUES(1,'Sut')");
        jdbc.update("INSERT INTO products(id,category_id,name,price,cost_price,stock_quantity,is_active,created_at) VALUES(1,1,'Sut',10000,6000,8,1,?),(2,1,'Non',3000,NULL,5,1,?),(3,1,'Eski',100,NULL,0,0,?)",utc(60),utc(60),utc(60));
        // Product 1 ledger: start of period 10, +5 receipt, +1 refund, −5 sales, −2 write-off, −1 count → 8.
        move(1,"OPENING",10,10,"P-1",20);move(1,"RECEIPT",5,15,"KR-1",5);move(1,"SALE",-3,12,"ORDER-1",3);move(1,"PAYMENT_REFUND",1,13,"ORDER-3",2);
        move(1,"WRITEOFF",-2,11,"INV-1",1);move(1,"COUNT",-1,10,"INV-2",1);move(1,"SALE",-2,8,"ORDER-2",0);move(2,"OPENING",5,5,"P-2",40);
        jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price,ordered_at,delivered_at,cancel_reason) VALUES(1,1,'N1','DELIVERED','PAID',30000,30000,?,?,NULL),(2,1,'N2','READY','PAID',20000,20000,?,NULL,NULL),(3,1,'N3','CANCELLED','PAID',10000,10000,?,NULL,'Mijoz rad etdi')",utc(3),utc(3),utc(1),utc(3));
        jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,unit_price,quantity,total_price,unit_cost_snapshot) VALUES(1,1,'Sut',10000,3,30000,6000),(2,1,'Sut',10000,2,20000,NULL),(3,1,'Sut',10000,1,10000,NULL)");
        jdbc.update("INSERT INTO delivery_partial_returns(order_id,delivery_job_id,status,reason,actor,refund_amount,refund_status,created_at) VALUES(1,1,'RECEIVED','Shikastlangan','c',10000,'PENDING',?)",utc(3));
        jdbc.update("INSERT INTO delivery_partial_return_items(order_id,product_id,product_name,quantity,refund_amount) VALUES(1,1,'Sut',1,10000)");
        jdbc.update("INSERT INTO suppliers(id,name,created_by,created_at,updated_at) VALUES(1,'Ta’minotchi',?,?,?)","t",utc(30),utc(30));
        jdbc.update("INSERT INTO warehouse_locations(id,code,name,type,created_by,created_at) VALUES(1,'QABUL','Qabul','RECEIVING','t',?),(2,'A01','A01','SHELF','t',?)",utc(30),utc(30));
        jdbc.update("INSERT INTO warehouse_receipts(id,supplier_name,invoice_number,status,created_by,created_at,posted_by,posted_at) VALUES(1,'Ta’minotchi','F-1','POSTED','t',?,'boss',?)",utc(5),utc(5));
        jdbc.update("INSERT INTO warehouse_receipt_items(receipt_id,product_id,product_name,quantity,location_id,unit_price) VALUES(1,1,'Sut',6,1,1000)");
        jdbc.update("INSERT INTO warehouse_receipt_counts(receipt_id,product_id,counted_quantity,unfit_quantity,unfit_condition,counted_by,counted_at) VALUES(1,1,5,1,'DAMAGED','t',?)",utc(5));
        jdbc.update("INSERT INTO supplier_invoices(receipt_id,supplier_id,invoice_number,invoice_amount,invoice_date,due_date,created_by,created_at) VALUES(1,1,'F-1',100000,?,?,'t',?)",today.minusDays(5),today.plusDays(10),utc(5));
        jdbc.update("INSERT INTO supplier_payment_records(supplier_id,receipt_id,amount,reference,paid_date,status,created_by,created_at) VALUES(1,1,40000,'B-1',?,'RECORDED','t',?)",today.minusDays(4),utc(4));
        jdbc.update("INSERT INTO inventory_batches(id,product_id,location_id,batch_number,expiry_date,unit_cost,physical_quantity,reference,created_by,created_at) VALUES(1,2,1,'P1',?,2000,4,'KR-9','ali',?)",today.plusDays(3),utc(2));
        jdbc.update("INSERT INTO inventory_documents(kind,status,product_id,location_id,reason,payload,cost_total,created_by,created_at,approved_by,approved_at) VALUES('WRITEOFF','POSTED',1,2,'Singan','{\"quantity\":2,\"reason_code\":\"WAREHOUSE_DAMAGE\",\"batch_id\":1}',12000,'ali',?,'boss',?)",utc(1),utc(1));
        jdbc.update("INSERT INTO inventory_documents(kind,status,location_id,reason,payload,created_by,created_at,approved_by,approved_at) VALUES('COUNT','POSTED',1,'Sanash','{\"variance_reason\":\"Yo‘qolgan\",\"lines\":[{\"batch_id\":1,\"product_id\":2,\"product_name\":\"Non\",\"expected_quantity\":5,\"counted_quantity\":4}]}','ali',?,'boss',?)",utc(1),utc(1));
        jdbc.update("INSERT INTO delivery_app_workers(id,worker_code,full_name,password_hash) VALUES(1,'K1','Kuryer Bir','x')");
        jdbc.update("INSERT INTO delivery_jobs(order_id,delivery_worker_id,customer_name,customer_phone,delivery_address,status,on_the_way_at,delivered_at) VALUES(1,1,'M','1','A','DELIVERED',?,?)",utc(3).minusMinutes(30),utc(3));
        jdbc.update("INSERT INTO tariff_plans(id,name,monthly_price) VALUES(1,'Oylik',50000)");
        jdbc.update("INSERT INTO user_tariffs(id,user_id,tariff_plan_id,status,end_date,activated_at) VALUES(1,1,1,'ACTIVE',?,?)",utc(-20),utc(2));
        jdbc.update("INSERT INTO payments(user_id,user_tariff_id,provider,amount,amount_tiyin,status,payme_perform_time) VALUES(1,1,'PAYME',50000,5000000,'PAID',?)",utc(2).toInstant(ZoneOffset.UTC).toEpochMilli());
        as("SUPER_ADMIN");
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    LocalDateTime utc(int daysAgo){return today.minusDays(daysAgo).atTime(12,0).atZone(ReportsService.TASHKENT).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();}
    void move(long product,String kind,long change,long after,String ref,int daysAgo){jdbc.update("INSERT INTO warehouse_stock_movements(product_id,product_name,kind,quantity_change,stock_after,reference,actor,note,event_key,created_at) VALUES(?,?,?,?,?,?,'t','',?,?)",product,"P"+product,kind,change,after,ref,UUID.randomUUID().toString(),utc(daysAgo));}
    void as(String... authorities){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u",null,Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));}
    List<Map<String,Object>> rows(String code,Map<String,String> filters){var f=new HashMap<>(Map.of("from",from,"to",to));f.putAll(filters);return (List<Map<String,Object>>)reports.run(code,f).get("rows");}
    Map<String,Object> row(String code,String key,Object value,Map<String,String> filters){return rows(code,filters).stream().filter(r->value.equals(r.get(key))).findFirst().orElseThrow(()->new AssertionError(code+" has no "+value));}
    static long n(Object v){return ((Number)v).longValue();}
    static void money(long expected,Object actual){assertEquals(0,BigDecimal.valueOf(expected).compareTo(new BigDecimal(String.valueOf(actual))),"expected "+expected+" got "+actual);}

    @Test void turnoverEndEqualsStartPlusInMinusOut(){
        var r=row("turnover","product_name","Sut",Map.of());
        assertEquals(10,n(r.get("start")));assertEquals(5,n(r.get("receipt")));assertEquals(1,n(r.get("customer_return")));assertEquals(5,n(r.get("sale")));
        assertEquals(2,n(r.get("writeoff")));assertEquals(1,n(r.get("adjust_minus")));assertEquals(8,n(r.get("end")));
        var idle=row("turnover","product_name","Non",Map.of());assertEquals(5,n(idle.get("start")));assertEquals(5,n(idle.get("end")));
    }
    @Test void stockNowAndAtADay(){
        var r=row("stock","product_name","Sut",Map.of());
        assertEquals(8,n(r.get("free")));assertEquals(2,n(r.get("reserved")),"paid order 2 is not handed over yet");assertEquals(10,n(r.get("physical")));
        money(60000,r.get("cost_sum"));money(100000,r.get("sale_sum"));
        assertEquals(15,n(row("stock","product_name","Sut",Map.of("date",today.minusDays(4).toString())).get("free")),"stock after the receipt, before the sales");
        assertTrue(rows("stock",Map.of()).stream().noneMatch(x->"Eski".equals(x.get("product_name"))),"no stock, not listed");
    }
    @Test void salesProfitTakesOffPartialReturns(){
        var r=row("sales","product_name","Sut",Map.of());
        assertEquals(2,n(r.get("quantity")));money(20000,r.get("sales"));money(12000,r.get("cost"));money(8000,r.get("profit"));assertEquals(new BigDecimal("40.0"),r.get("margin"));
        assertEquals(1,rows("sales",Map.of("group","category")).size());assertEquals(1,rows("sales",Map.of("group","day")).size());
    }
    @Test void reorderUsesCalendarDays(){
        var r=row("reorder","product_name","Sut",Map.of());
        assertEquals(5,n(r.get("sold")),"orders 1 and 2; the cancelled one is left out");assertEquals(0.5,r.get("daily_avg"));
        assertEquals(17.6,r.get("days_cover"));assertEquals(0,n(r.get("need_7")));assertEquals(6,n(r.get("need_30")));
    }
    @Test void suppliersCalendarAndTurnover(){
        var c=row("payment-calendar","receipt","KR-1",Map.of());money(60000,c.get("outstanding"));assertEquals(10L,n(c.get("days_left")));
        var t=row("supplier-turnover","supplier_name","Ta’minotchi",Map.of());money(0,t.get("start"));money(100000,t.get("receipts"));money(40000,t.get("payments"));money(60000,t.get("end"));
        var k=row("receipts","receipt","KR-1",Map.of());assertEquals(-1,n(k.get("difference")));assertEquals(1,n(k.get("unfit_quantity")));
    }
    @Test void warehouseOrderAndOtherReportsRun(){
        assertEquals(1,rows("bins",Map.of()).size());
        var u=row("unplaced","product_name","Non",Map.of());assertEquals("ali",u.get("received_by"));assertEquals(2L,n(u.get("days")));
        var w=row("writeoffs","product_name","Sut",Map.of());assertEquals(2,n(w.get("quantity")));assertEquals("WAREHOUSE_DAMAGE",w.get("reason_code"));assertEquals(new BigDecimal("40.0"),w.get("sales_share"));
        assertEquals(3L,n(row("expiry","product_name","Non",Map.of()).get("days_left")));
        var cnt=row("counts","product_name","Non",Map.of());assertEquals(-1,n(cnt.get("difference")));money(-2000,cnt.get("difference_sum"));assertEquals("Yo‘qolgan",cnt.get("reason"));
        var dead=rows("dead-stock",Map.of());assertEquals(1,dead.size());assertEquals("Non",dead.get(0).get("product_name"));
        assertEquals(6,rows("order-flow",Map.of()).size());rows("order-flow",Map.of("group","packer"));
        var problems=rows("problems",Map.of());
        assertTrue(problems.stream().anyMatch(p->"PAID_NOT_DELIVERED".equals(p.get("problem"))&&"N2".equals(p.get("order_number"))));
        assertTrue(problems.stream().anyMatch(p->"PAID_CANCELLED".equals(p.get("problem"))&&"N3".equals(p.get("order_number"))));
        assertTrue(problems.stream().anyMatch(p->"REFUND_PENDING".equals(p.get("problem"))&&"N1".equals(p.get("order_number"))));
        var reason=row("reasons","reason","Mijoz rad etdi",Map.of());assertEquals(1,n(reason.get("orders")));assertEquals(new BigDecimal("33.3"),reason.get("share"));
        var courier=row("couriers","courier","Kuryer Bir",Map.of());assertEquals(1,n(courier.get("delivered")));assertEquals(30.0,((Number)courier.get("avg_minutes")).doubleValue());
        var plan=row("subscriptions","plan","Oylik",Map.of());assertEquals(1,n(plan.get("active")));assertEquals(1,n(plan.get("new")));money(50000,plan.get("income"));
    }
    @Test void staffSeeOnlyTheirReportsAndNoMoney(){
        as("ROLE_ADMIN","PERM:*","PERM:warehouse.view");
        var r=row("stock","product_name","Sut",Map.of());
        assertFalse(r.containsKey("cost_sum"));assertFalse(r.containsKey("sale_sum"));assertEquals(8,n(r.get("free")));
        assertThrows(AccessDeniedException.class,()->rows("sales",Map.of()));
        assertThrows(AccessDeniedException.class,()->rows("problems",Map.of()));
        assertEquals(List.of("stock","turnover","bins","unplaced","receipts","writeoffs","expiry","counts","dead-stock","reorder"),reports.available());
    }
}
