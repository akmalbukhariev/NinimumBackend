package com.ninimum.api.warehouse;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Batch inventory end to end: opening count, FEFO reservation and issue, write-off, blind count. Isolated warehouse_test only. */
@EnabledIfSystemProperty(named="warehouse.test.jdbc",matches=".*")
class InventoryIntegrationTest {
    static final String PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    JdbcTemplate jdbc;WarehouseService warehouse;InventoryService inventory;TransactionTemplate tx;DataSourceTransactionManager transactions;long bin,cold;

    @SuppressWarnings("unchecked") private <T> T proxy(T target){var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy();}
    @BeforeEach void setup(){
        String url=System.getProperty("warehouse.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/warehouse_test"))throw new IllegalArgumentException("Only isolated loopback warehouse_test is permitted");
        var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String table:List.of("inventory_products","inventory_batches","inventory_allocations","inventory_documents","inventory_bin_locks","inventory_movements","inventory_settings","delivery_partial_returns","warehouse_receipt_counts","warehouse_receipt_items","warehouse_stock_movements","warehouse_receipts","warehouse_locations","warehouse_schema","payments","order_items","delivery_jobs","orders","products"))jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,name VARCHAR(255),sku VARCHAR(80),barcode VARCHAR(80),stock_quantity INT NOT NULL,is_active BOOLEAN NOT NULL,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,order_number VARCHAR(100),payment_status VARCHAR(20),status VARCHAR(30),total_price DECIMAL(12,2),ordered_at DATETIME,delivered_at DATETIME,updated_at DATETIME,status_actor VARCHAR(255),status_source VARCHAR(40),status_reason VARCHAR(500))");
        jdbc.execute("CREATE TABLE order_items(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT,product_id BIGINT,product_name VARCHAR(255),quantity INT)");
        jdbc.execute("CREATE TABLE delivery_jobs(id BIGINT PRIMARY KEY,order_id BIGINT UNIQUE,status VARCHAR(30))");
        jdbc.execute("CREATE TABLE payments(id BIGINT PRIMARY KEY,order_id BIGINT,status VARCHAR(20))");
        jdbc.update("INSERT INTO products(id,name,barcode,stock_quantity,is_active) VALUES (1,'Milk','4780001',10,1)");
        new ResourceDatabasePopulator(new FileSystemResource("src/test/resources/live-schema-fixtures/warehouse-step1.sql")).execute(ds);
        for(String file:List.of("20261012_partial_returns.sql","20261017_receipt_approval.sql","20261017_location_hierarchy.sql","20261017_receipt_metadata.sql","20261017_batch_inventory.sql")){var m=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/"+file));m.execute(ds);if(file.contains("inventory"))m.execute(ds);}
        transactions=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(transactions);
        WarehouseService target=new WarehouseService(jdbc);ReflectionTestUtils.setField(target,"enabled",true);ReflectionTestUtils.setField(target,"inventoryEnabled",true);target.verifySchema();warehouse=proxy(target);
        inventory=proxy(new InventoryService(jdbc,warehouse));
        owner();
        bin=warehouse.createLocation(Map.of("code","A-01","name","Shelf","type","SHELF"));
        cold=warehouse.createLocation(Map.of("code","C-01","name","Fridge","type","COLD_STORAGE"));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void owner(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner",null,List.of(new SimpleGrantedAuthority("SUPER_ADMIN"))));}
    long stock(){return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Long.class);}
    long physical(){return jdbc.queryForObject("SELECT COALESCE(SUM(physical_quantity),0) FROM inventory_batches WHERE product_id=1",Long.class);}
    Map<String,Object> line(long location,int qty,String cost,int days,String lot){return Map.of("location_id",location,"quantity",qty,"unit_price",cost,"expiry_date",LocalDate.now().plusDays(days).toString(),"batch_number",lot);}
    long open(){
        long id=inventory.create(Map.of("kind","OPENING","reason","Opening count","product_id",1,"lines",List.of(line(bin,6,"5",30,"L1"),line(cold,4,"6",60,"L2"))));
        inventory.approve(id,Map.of());return id;
    }

    @Test void openingCountMovesProductIntoBatchTrackingWithoutChangingSellableStock(){
        open();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_products WHERE product_id=1",Integer.class));
        assertEquals(10,stock());assertEquals(10,physical());
        assertEquals(2,((List<?>)inventory.balances(1L,null,1).get("items")).size());
        assertThrows(WarehouseException.class,()->inventory.create(Map.of("kind","OPENING","reason","Again","product_id",1,"lines",List.of(line(bin,1,"5",30,"L9")))));
    }

    @Test void saleReservesTheEarliestExpiryBatchFirstAndIssueRemovesPhysicalStock(){
        open();
        jdbc.update("INSERT INTO orders(id,order_number,payment_status,status) VALUES(7,'N7','PAID','CONFIRMED')");
        jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,quantity) VALUES(7,1,'Milk',8)");
        tx.executeWithoutResult(t->{warehouse.lockStock(1);jdbc.update("UPDATE products SET stock_quantity=stock_quantity-8 WHERE id=1");warehouse.recordChange(1,-8,"SALE","ORDER-7","test","Paid order","sale:7");});
        assertEquals(2,stock());
        var allocations=jdbc.queryForList("SELECT a.quantity,b.batch_number FROM inventory_allocations a JOIN inventory_batches b ON b.id=a.batch_id WHERE a.order_id=7 ORDER BY b.expiry_date");
        assertEquals(List.of("L1","L2"),allocations.stream().map(a->a.get("batch_number")).toList());
        assertEquals(6L,((Number)allocations.get(0).get("quantity")).longValue());assertEquals(2L,((Number)allocations.get(1).get("quantity")).longValue());
        assertEquals(10,physical());
        tx.executeWithoutResult(t->warehouse.issueOrderStock(7));
        assertEquals(2,physical());assertEquals(2,stock());
        assertEquals(0,jdbc.queryForObject("SELECT COALESCE(SUM(reserved_quantity),0) FROM inventory_batches",Long.class));
        assertEquals(0,new BigDecimal("5.4000").compareTo(jdbc.queryForObject("SELECT unit_cost_snapshot FROM order_items WHERE order_id=7",BigDecimal.class)));
    }

    @Test void writeOffNeedsAPhotoAndRemovesStockAtBatchCost(){
        open();
        long batch=jdbc.queryForObject("SELECT id FROM inventory_batches WHERE batch_number='L1'",Long.class);
        assertThrows(WarehouseException.class,()->inventory.create(Map.of("kind","WRITEOFF","reason","Broken","batch_id",batch,"quantity",2,"reason_code","WAREHOUSE_DAMAGE")));
        long id=inventory.create(Map.of("kind","WRITEOFF","reason","Broken","batch_id",batch,"quantity",2,"reason_code","WAREHOUSE_DAMAGE","photo_base64",PNG));
        assertEquals(10,stock());
        inventory.approve(id,Map.of());
        assertEquals(8,stock());assertEquals(8,physical());
        assertEquals(0,new BigDecimal("10").compareTo(jdbc.queryForObject("SELECT cost_total FROM inventory_documents WHERE id=?",BigDecimal.class,id)));
    }

    @Test void blindCountLocksTheBinAndAppliesTheCountedDifferenceAfterApproval(){
        open();
        long id=inventory.create(Map.of("kind","COUNT","reason","Monthly count","location_id",bin,"source_code","A-01"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_bin_locks WHERE location_id=?",Integer.class,bin));
        @SuppressWarnings("unchecked") var lines=(List<Map<String,Object>>)((Map<String,Object>)inventory.document(id).get("payload")).get("lines");
        assertFalse(lines.get(0).containsKey("expected_quantity"),"expected quantity must stay hidden while counting");
        long batch=jdbc.queryForObject("SELECT id FROM inventory_batches WHERE batch_number='L1'",Long.class);
        var counts=List.of(Map.of("batch_id",batch,"quantity",5,"scanned_barcode","4780001"));
        assertThrows(WarehouseException.class,()->inventory.submitCount(id,Map.of("counts",counts)),"a difference needs a reason");
        inventory.submitCount(id,Map.of("counts",counts,"variance_reason","One pack missing"));
        assertEquals("COUNTED",jdbc.queryForObject("SELECT status FROM inventory_documents WHERE id=?",String.class,id));
        inventory.approve(id,Map.of());
        assertEquals(9,stock());assertEquals(9,physical());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_bin_locks",Integer.class));
        assertEquals("POSTED",jdbc.queryForObject("SELECT status FROM inventory_documents WHERE id=?",String.class,id));
    }
    @Test void warehouseAppStartsACountFromTheBinLabelAndSeesOnlyOpenCounts(){
        open();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("W01",null,List.of(new SimpleGrantedAuthority("WAREHOUSE"))));
        assertEquals("INVENTORY_BIN_MISMATCH",assertThrows(WarehouseException.class,()->inventory.create(Map.of("kind","COUNT","reason","Shelf count","source_code","Z-99"))).getMessage());
        jdbc.update("UPDATE inventory_batches SET physical_quantity=0 WHERE location_id=?",cold);
        assertEquals("INVENTORY_BIN_EMPTY",assertThrows(WarehouseException.class,()->inventory.create(Map.of("kind","COUNT","reason","Empty shelf","source_code","C-01"))).getMessage());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_bin_locks",Integer.class));
        jdbc.update("UPDATE inventory_batches SET physical_quantity=4 WHERE location_id=?",cold);
        long id=inventory.create(Map.of("kind","COUNT","reason","Shelf count","source_code","a-01"));
        assertEquals(bin,jdbc.queryForObject("SELECT location_id FROM inventory_documents WHERE id=?",Long.class,id));
        @SuppressWarnings("unchecked") var open=(List<Map<String,Object>>)inventory.documents(1,"COUNT","DRAFT").get("items");
        assertEquals(List.of(id),open.stream().map(r->((Number)r.get("id")).longValue()).toList());assertEquals("A-01",open.get(0).get("location_code"));
        assertEquals(0L,inventory.documents(1,"COUNT","COUNTED").get("total"));
        long batch=jdbc.queryForObject("SELECT id FROM inventory_batches WHERE batch_number='L1'",Long.class);
        var counts=List.of(Map.of("batch_id",batch,"quantity",5,"scanned_barcode","4780001"));
        assertEquals("INVENTORY_VARIANCE_REASON_REQUIRED",assertThrows(WarehouseException.class,()->inventory.submitCount(id,Map.of("counts",counts,"variance_reason"," "))).getMessage());
        inventory.submitCount(id,Map.of("counts",List.of(Map.of("batch_id",batch,"quantity",6,"scanned_barcode","4780001"))));
        assertEquals("COUNTED",jdbc.queryForObject("SELECT status FROM inventory_documents WHERE id=?",String.class,id));
        assertThrows(RuntimeException.class,()->inventory.approve(id,Map.of()),"the worker cannot approve their own count");
        owner();inventory.approve(id,Map.of());assertEquals(10,stock());
    }
}
