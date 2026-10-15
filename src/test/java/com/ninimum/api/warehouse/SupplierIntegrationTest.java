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
        for(String t:List.of("supplier_payment_records","supplier_invoices","suppliers","warehouse_receipt_items","warehouse_receipts","warehouse_locations","warehouse_stock_movements","products"))jdbc.execute("DROP TABLE IF EXISTS "+t);
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,name VARCHAR(100),stock_quantity INT,is_active BOOLEAN,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE warehouse_locations(id BIGINT PRIMARY KEY,code VARCHAR(50),is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE warehouse_receipts(id BIGINT AUTO_INCREMENT PRIMARY KEY,supplier_name VARCHAR(160),invoice_number VARCHAR(80),note VARCHAR(500),status VARCHAR(30),created_by VARCHAR(160),created_at DATETIME,posted_by VARCHAR(160),posted_at DATETIME,reversed_by VARCHAR(160),reversed_at DATETIME,reversal_reason VARCHAR(500))");
        jdbc.execute("CREATE TABLE warehouse_receipt_items(id BIGINT AUTO_INCREMENT PRIMARY KEY,receipt_id BIGINT,product_id BIGINT,product_name VARCHAR(100),quantity INT,location_id BIGINT)");
        jdbc.execute("CREATE TABLE warehouse_stock_movements(id BIGINT AUTO_INCREMENT PRIMARY KEY,product_id BIGINT,product_name VARCHAR(100),kind VARCHAR(40),quantity_change BIGINT,stock_after BIGINT,reference VARCHAR(160),actor VARCHAR(160),note VARCHAR(500),event_key VARCHAR(200) UNIQUE,created_at DATETIME)");
        jdbc.update("INSERT INTO products VALUES(1,'Test product',0,1,NOW())");jdbc.update("INSERT INTO warehouse_locations VALUES(1,'A1',1)");
        var script=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261015_suppliers.sql"));script.execute(ds);script.execute(ds);
        suppliers=new SupplierService(jdbc);warehouse=new WarehouseService(jdbc);ReflectionTestUtils.setField(warehouse,"enabled",true);
    }
    long supplier(){return tx.execute(s->suppliers.save(null,Map.of("name","  Supplier  ","payment_term_days",7)));}
    long receipt(long supplier,String invoice){return tx.execute(s->warehouse.createReceipt(Map.of("supplier_id",supplier,"invoice_number",invoice,"invoice_amount","1000.00","invoice_date","2026-10-01","items",List.of(Map.of("product_id",1,"quantity",2,"location_id",1)))));}
    Map<String,Object> payment(long receipt,String amount,String reference){return Map.of("receipt_id",receipt,"amount",amount,"reference",reference,"paid_date","2026-10-02");}
    BigDecimal balance(long supplier){return new BigDecimal(suppliers.detail(supplier).get("outstanding_amount").toString());}
    @Test void completeReceiptRecordPaymentReconcileAndVoidFlow() {
        long id=supplier(),receipt=receipt(id,"INV-1");assertEquals("Supplier",suppliers.detail(id).get("name"));
        assertEquals(0,balance(id).signum());
        assertEquals("2026-10-08",jdbc.queryForObject("SELECT CAST(due_date AS CHAR) FROM supplier_invoices WHERE receipt_id=?",String.class,receipt));
        tx.execute(s->warehouse.postReceipt(receipt));tx.execute(s->warehouse.postReceipt(receipt));
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
        tx.execute(s->warehouse.postReceipt(r));tx.execute(s->suppliers.recordPayment(id,payment(r,"100","BANK")));
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
    @Test void concurrentRecordsCannotOverpayTheInvoice() throws Exception {
        long id=supplier(),r=receipt(id,"INV-CONCURRENT");tx.execute(s->warehouse.postReceipt(r));
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
}
