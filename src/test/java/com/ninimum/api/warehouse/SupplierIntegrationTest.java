package com.ninimum.api.warehouse;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="supplier.test.jdbc",matches=".*")
class SupplierIntegrationTest {
    JdbcTemplate jdbc; SupplierService suppliers; WarehouseService warehouse; TransactionTemplate tx;
    @BeforeEach void setup() {
        String url=System.getProperty("supplier.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/supplier_test"))throw new IllegalArgumentException("Isolated local supplier_test only");
        var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        for(String t:List.of("warehouse_receipt_counts","supplier_payment_records","supplier_invoices","suppliers","warehouse_receipt_items","warehouse_receipts","warehouse_locations","warehouse_stock_movements","products"))jdbc.execute("DROP TABLE IF EXISTS "+t);
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,name VARCHAR(100),stock_quantity INT,is_active BOOLEAN,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE warehouse_locations(id BIGINT AUTO_INCREMENT PRIMARY KEY,code VARCHAR(50),name VARCHAR(160),type VARCHAR(20) NOT NULL DEFAULT 'SHELF',is_active BOOLEAN DEFAULT TRUE,created_by VARCHAR(160),created_at DATETIME)");
        jdbc.execute("CREATE TABLE warehouse_receipts(id BIGINT AUTO_INCREMENT PRIMARY KEY,supplier_name VARCHAR(160),invoice_number VARCHAR(80),note VARCHAR(500),status VARCHAR(30),created_by VARCHAR(160),created_at DATETIME,posted_by VARCHAR(160),posted_at DATETIME,reversed_by VARCHAR(160),reversed_at DATETIME,reversal_reason VARCHAR(500))");
        jdbc.execute("CREATE TABLE warehouse_receipt_items(id BIGINT AUTO_INCREMENT PRIMARY KEY,receipt_id BIGINT,product_id BIGINT,product_name VARCHAR(100),quantity INT,location_id BIGINT)");
        jdbc.execute("CREATE TABLE warehouse_stock_movements(id BIGINT AUTO_INCREMENT PRIMARY KEY,product_id BIGINT,product_name VARCHAR(100),kind VARCHAR(40),quantity_change BIGINT,stock_after BIGINT,reference VARCHAR(160),actor VARCHAR(160),note VARCHAR(500),event_key VARCHAR(200) UNIQUE,created_at DATETIME)");
        jdbc.update("INSERT INTO products VALUES(1,'Test product',0,1,NOW())");jdbc.update("INSERT INTO warehouse_locations(id,code,is_active) VALUES(1,'A1',1)");
        var script=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261015_suppliers.sql"));script.execute(ds);script.execute(ds);
        new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261017_receipt_approval.sql")).execute(ds);
        new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261017_location_hierarchy.sql")).execute(ds);
        var metadataMigration=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261017_receipt_metadata.sql"));metadataMigration.execute(ds);metadataMigration.execute(ds);
        suppliers=new SupplierService(jdbc);warehouse=new WarehouseService(jdbc);ReflectionTestUtils.setField(warehouse,"enabled",true);
    }
    long post(long id) {
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previous=context.getAuthentication();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("owner",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SUPER_ADMIN"))));
        try{return warehouse.postReceipt(id,Map.of("counts",List.of(Map.of("product_id",1,"quantity",2))));}finally{context.setAuthentication(previous);}
    }
    @Test void receiptNeedsBlindCountAndSeparateApproverExceptOwner() {
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("creator",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN"))));
        try {
            long supplier=supplier(),r=receipt(supplier,"BLIND");
            assertThrows(WarehouseException.class,()->tx.execute(s->warehouse.postReceipt(r,Map.of("counts",List.of(Map.of("product_id",1,"quantity",2))))));
            var draft=warehouse.receipt(r);assertFalse(((List<Map<String,Object>>)draft.get("items")).get(0).containsKey("quantity"));
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("checker",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN"))));
            assertThrows(WarehouseException.class,()->tx.execute(s->warehouse.postReceipt(r,Map.of())));
            assertThrows(WarehouseException.class,()->tx.execute(s->warehouse.postReceipt(r,Map.of("counts",List.of(Map.of("product_id",1,"quantity",3))))));
            assertEquals(0,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Integer.class));
            tx.execute(s->warehouse.postReceipt(r,Map.of("counts",List.of(Map.of("product_id",1,"quantity",2)))));
            assertEquals("checker",jdbc.queryForObject("SELECT counted_by FROM warehouse_receipt_counts WHERE receipt_id=?",String.class,r));
        }finally{context.setAuthentication(null);}
    }
    @Test void locationHierarchyAcceptsZoneRowBinAndRejectsWrongParents() {
        long zone=tx.execute(s->warehouse.createLocation(Map.of("code","Z1","name","Zone","type","RECEIVING","hierarchy_level","ZONE")));
        long row=tx.execute(s->warehouse.createLocation(Map.of("code","R1","name","Row","type","COLD_STORAGE","hierarchy_level","ROW","parent_id",zone)));
        long bin=tx.execute(s->warehouse.createLocation(Map.of("code","B1","name","Bin","type","PICKING","hierarchy_level","BIN","parent_id",row)));
        assertEquals("Z1 / R1 / B1",warehouse.locations().stream().filter(v->((Number)v.get("id")).longValue()==bin).findFirst().orElseThrow().get("path"));
        assertThrows(WarehouseException.class,()->tx.execute(s->warehouse.createLocation(Map.of("code","BAD","name","Bad","type","SHELF","hierarchy_level","BIN","parent_id",zone))));
        assertThrows(WarehouseException.class,()->tx.execute(s->warehouse.createLocation(Map.of("code","BAD2","name","Bad","type","SHELF","hierarchy_level","ROW"))));
        long supplier=supplier();
        assertThrows(WarehouseException.class,()->tx.execute(s->warehouse.createReceipt(Map.of("supplier_id",supplier,"invoice_number","ZONE","invoice_amount","1000","invoice_date","2026-10-01","items",List.of(Map.of("product_id",1,"quantity",2,"location_id",zone,"unit_price","500","expiry_date",java.time.LocalDate.now().plusYears(1).toString()))))));
    }
    @Test void receiptMetadataConvertsBlocksAndCannotApproveExpiredDraftOrEditPostedReceipt() {
        long supplier=supplier();
        long id=tx.execute(s->warehouse.createReceipt(Map.of("supplier_id",supplier,"invoice_number","BLOCKS","invoice_amount","1000","invoice_date","2026-10-01","items",List.of(Map.of("product_id",1,"quantity",1,"input_unit","BLOCK","units_per_block",2,"location_id",1,"unit_price","500","expiry_date",java.time.LocalDate.now().plusYears(1).toString())))));
        assertEquals(2,jdbc.queryForObject("SELECT quantity FROM warehouse_receipt_items WHERE receipt_id=?",Integer.class,id));
        jdbc.update("UPDATE warehouse_receipt_items SET expiry_date='2000-01-01' WHERE receipt_id=?",id);
        assertThrows(WarehouseException.class,()->tx.execute(s->post(id)));
        assertEquals(0,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Integer.class));
        tx.executeWithoutResult(s->warehouse.updateReceiptMetadata(id,1,Map.of("unit_price","450.50","expiry_date",java.time.LocalDate.now().plusYears(1).toString(),"batch_number","LOT-A")));
        tx.execute(s->post(id));assertEquals(2,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Integer.class));
        assertThrows(WarehouseException.class,()->tx.executeWithoutResult(s->warehouse.updateReceiptMetadata(id,1,Map.of("unit_price","1","expiry_date",java.time.LocalDate.now().plusYears(1).toString()))));
        assertEquals(new BigDecimal("450.50"),jdbc.queryForObject("SELECT unit_price FROM warehouse_receipt_items WHERE receipt_id=?",BigDecimal.class,id));
    }
    // TZ v12 A4.1: 2 × 400 with an invoice of 1 000 stops without a reason; with one the debt is 800.
    @Test void invoiceTotalDifferentFromLinesNeedsAReasonAndDebtFollowsTheLines() {
        long supplier=supplier();
        var line=Map.of("product_id",1,"quantity",2,"location_id",1,"unit_price","400","expiry_date",java.time.LocalDate.now().plusYears(1).toString());
        var error=assertThrows(WarehouseException.class,()->tx.execute(s->warehouse.createReceipt(Map.of("supplier_id",supplier,"invoice_number","DIFF","invoice_amount","1000","invoice_date","2026-10-01","items",List.of(line)))));
        assertEquals("RECEIPT_INVOICE_DIFFERENCE",error.getMessage());
        long id=tx.execute(s->warehouse.createReceipt(Map.of("supplier_id",supplier,"invoice_number","DIFF","invoice_amount","1000","invoice_date","2026-10-01","invoice_difference_reason","Yetkazish narxi","items",List.of(line))));
        assertEquals(0,new BigDecimal("800").compareTo(jdbc.queryForObject("SELECT invoice_amount FROM supplier_invoices WHERE receipt_id=?",BigDecimal.class,id)));
        assertTrue(jdbc.queryForObject("SELECT note FROM warehouse_receipts WHERE id=?",String.class,id).contains("Yetkazish narxi"));
        tx.executeWithoutResult(s->warehouse.updateReceiptMetadata(id,1,Map.of("unit_price","450","expiry_date",java.time.LocalDate.now().plusYears(1).toString())));
        assertEquals(0,new BigDecimal("900").compareTo(jdbc.queryForObject("SELECT invoice_amount FROM supplier_invoices WHERE receipt_id=?",BigDecimal.class,id)),"a price changed on the draft moves the debt");
    }
    long supplier(){return tx.execute(s->suppliers.save(null,Map.of("name","  Supplier  ","payment_term_days",7)));}
    long receipt(long supplier,String invoice){return tx.execute(s->warehouse.createReceipt(Map.of("supplier_id",supplier,"invoice_number",invoice,"invoice_amount","1000.00","invoice_date","2026-10-01","items",List.of(Map.of("product_id",1,"quantity",2,"location_id",1,"unit_price","500","expiry_date",java.time.LocalDate.now().plusYears(1).toString())))));}
    Map<String,Object> payment(long receipt,String amount,String reference){return Map.of("receipt_id",receipt,"amount",amount,"reference",reference,"paid_date","2026-10-02");}
    BigDecimal balance(long supplier){return new BigDecimal(suppliers.detail(supplier).get("outstanding_amount").toString());}
    @Test void completeReceiptRecordPaymentReconcileAndVoidFlow() {
        long id=supplier(),receipt=receipt(id,"INV-1");assertEquals("Supplier",suppliers.detail(id).get("name"));
        assertEquals(0,balance(id).signum());
        assertEquals("2026-10-08",jdbc.queryForObject("SELECT CAST(due_date AS CHAR) FROM supplier_invoices WHERE receipt_id=?",String.class,receipt));
        tx.execute(s->post(receipt));tx.execute(s->post(receipt));
        assertEquals(2,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Integer.class));
        assertEquals(0,balance(id).compareTo(new BigDecimal("1000")));
        long p=tx.execute(s->suppliers.recordPayment(id,payment(receipt,"400","BANK-1")));
        long replay=tx.execute(s->suppliers.recordPayment(id,payment(receipt,"400","BANK-1")));
        assertEquals(p,replay);
        assertEquals(0,balance(id).compareTo(new BigDecimal("600")));
        assertEquals(1,suppliers.calendar("2026-10-01","2026-10-31").size());
        assertThrows(WarehouseException.class,()->tx.execute(s->suppliers.recordPayment(id,payment(receipt,"700","BANK-2"))));
        assertThrows(WarehouseException.class,()->tx.execute(s->suppliers.recordPayment(id,payment(receipt,"401","BANK-1"))));
        tx.executeWithoutResult(s->suppliers.voidPayment(id,p,"Wrong document"));tx.executeWithoutResult(s->suppliers.voidPayment(id,p,"Retry"));
        assertEquals("Wrong document",jdbc.queryForObject("SELECT void_reason FROM supplier_payment_records WHERE id=?",String.class,p));
        assertEquals(0,balance(id).compareTo(new BigDecimal("1000")));
        tx.execute(s->suppliers.recordPayment(id,payment(receipt,"1000","BANK-3")));
        assertEquals(0,balance(id).signum());assertTrue(suppliers.calendar("2026-10-01","2026-10-31").isEmpty());
    }
    @Test void duplicatesAndInactiveSuppliersDoNotLeaveOrphanReceipts() {
        long id=supplier();receipt(id,"INV-1");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->receipt(id,"INV-1"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_receipts",Integer.class));
        tx.execute(s->suppliers.save(id,Map.of("name","Supplier","is_active",false)));
        assertThrows(WarehouseException.class,()->receipt(id,"INV-2"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_receipts",Integer.class));
    }
    @Test void draftCannotReceivePaymentAndReversalRetainsCreditAndHistory() {
        long id=supplier(),r=receipt(id,"INV-1");
        assertThrows(WarehouseException.class,()->tx.execute(s->suppliers.recordPayment(id,payment(r,"100","DRAFT"))));
        tx.execute(s->post(r));tx.execute(s->suppliers.recordPayment(id,payment(r,"100","BANK")));
        tx.execute(s->warehouse.reverseReceipt(r,"Returned to supplier"));
        assertEquals(0,balance(id).compareTo(new BigDecimal("-100")));
        assertEquals(0,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Integer.class));
        assertThrows(WarehouseException.class,()->tx.execute(s->suppliers.recordPayment(id,payment(r,"10","AFTER"))));
    }
    @Test void termsAreSnapshottedAndValidationRejectsInvalidMoneyAndDates() {
        long id=supplier(),r=receipt(id,"INV-1");
        tx.execute(s->suppliers.save(id,Map.of("name","Renamed supplier","payment_term_days",30)));
        assertEquals("2026-10-08",jdbc.queryForObject("SELECT CAST(due_date AS CHAR) FROM supplier_invoices WHERE receipt_id=?",String.class,r));
        assertEquals("Supplier",jdbc.queryForObject("SELECT supplier_name FROM warehouse_receipts WHERE id=?",String.class,r));
        assertThrows(WarehouseException.class,()->suppliers.save(null,Map.of("name","Bad","payment_term_days",-1)));
        for(String amount:List.of("0","-1","1.001","NaN"))assertThrows(WarehouseException.class,()->SupplierService.invoiceAmount(amount));
        assertThrows(WarehouseException.class,()->SupplierService.invoiceDate("2026-02-30"));
        assertThrows(WarehouseException.class,()->suppliers.calendar("2026-10-20","2026-10-01"));
    }
    @Test void settledInvoiceIsNotOverdueAndVoidRestoresOverdueStatus() {
        long id=supplier(),r=receipt(id,"INV-OVERDUE");
        jdbc.update("UPDATE supplier_invoices SET due_date=DATE(UTC_TIMESTAMP()+INTERVAL 5 HOUR)-INTERVAL 1 DAY WHERE receipt_id=?",r);
        assertEquals(0,overdue(id));
        tx.execute(s->post(r));
        assertEquals(1,overdue(id));
        long payment=tx.execute(s->suppliers.recordPayment(id,payment(r,"1000","SETTLED")));
        assertEquals(0,overdue(id));
        tx.executeWithoutResult(s->suppliers.voidPayment(id,payment,"Test correction"));
        assertEquals(1,overdue(id));
        tx.execute(s->warehouse.reverseReceipt(r,"Test reversal"));
        assertEquals(0,overdue(id));
    }
    @SuppressWarnings("unchecked")
    int overdue(long id) {
        var invoices=(List<Map<String,Object>>)suppliers.detail(id).get("invoices");
        return ((Number)invoices.get(0).get("overdue")).intValue();
    }
    @Test void concurrentRecordsCannotOverpayTheInvoice() throws Exception {
        long id=supplier(),r=receipt(id,"INV-CONCURRENT");tx.execute(s->post(r));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var tasks=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(String reference:List.of("FIRST","SECOND"))tasks.add(pool.submit(()->{
                start.await();
                try{tx.execute(s->suppliers.recordPayment(id,payment(r,"700",reference)));return true;}
                catch(WarehouseException ex){return false;}
            }));
            start.countDown();int successes=0;
            for(var result:tasks)if(result.get(10,java.util.concurrent.TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);assertEquals(0,balance(id).compareTo(new BigDecimal("300")));
        } finally {pool.shutdownNow();}
    }
    // Q-16 period closing: closes the books until a date on this test DB; returns a cleanup that reopens everything.
    Runnable closeUntil(String day){
        jdbc.execute("DROP TABLE IF EXISTS period_closings");
        for(String f:List.of("20261021_strict_rules.sql","20261022_period_close.sql"))new ResourceDatabasePopulator(new FileSystemResource("database/migrations/"+f)).execute(jdbc.getDataSource());
        new com.ninimum.api.finance.PeriodClose(jdbc);
        jdbc.update("INSERT INTO period_closings(closed_until,changed_by,changed_at) VALUES(?,'test',UTC_TIMESTAMP())",day);
        return()->jdbc.update("DELETE FROM period_closings");
    }
    @Test void closedPeriodBlocksSupplierPaymentsAndVoids() {
        long id=supplier(),receipt=receipt(id,"INV-Q16");tx.execute(s->post(receipt));
        long p=tx.execute(s->suppliers.recordPayment(id,payment(receipt,"100","BANK-Q16")));
        Runnable reopen=closeUntil("2026-10-05");
        try{
            assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,()->tx.executeWithoutResult(s->suppliers.voidPayment(id,p,"Wrong"))).getMessage());
            assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,()->tx.execute(s->suppliers.recordPayment(id,payment(receipt,"50","BANK-Q16B")))).getMessage());
            assertEquals("RECORDED",jdbc.queryForObject("SELECT status FROM supplier_payment_records WHERE id=?",String.class,p));
        }finally{reopen.run();}
        tx.executeWithoutResult(s->suppliers.voidPayment(id,p,"Wrong"));
    }
}
