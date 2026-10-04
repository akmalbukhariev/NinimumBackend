package com.ninimum.api.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.payme.PaymeRequest;
import com.ninimum.api.payment.service.IPaymeService;
import com.ninimum.api.payment.service.PaymeMapper;
import com.ninimum.api.payment.service.impl.PaymeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in destructive fixture, restricted to an isolated database named warehouse_test. */
@EnabledIfSystemProperty(named="warehouse.test.jdbc",matches=".*")
class WarehouseIntegrationTest {
    DriverManagerDataSource ds;
    JdbcTemplate jdbc;
    WarehouseService service;
    TransactionTemplate tx;
    DataSourceTransactionManager transactions;
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return (T)proxy.getProxy();
    }
    @BeforeEach void setup() {
        String url=System.getProperty("warehouse.test.jdbc");
        if (!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/warehouse_test"))
            throw new IllegalArgumentException("Only isolated loopback warehouse_test is permitted");
        ds=new DriverManagerDataSource(url,"root","");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table: List.of("warehouse_receipt_items","warehouse_stock_movements","warehouse_receipts","warehouse_locations","warehouse_schema","payments","order_items","delivery_jobs","orders","products"))
            jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,name VARCHAR(255),sku VARCHAR(80),barcode VARCHAR(80),stock_quantity INT NOT NULL,is_active BOOLEAN NOT NULL,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,order_number VARCHAR(100),payment_status VARCHAR(20),status VARCHAR(30),total_price DECIMAL(12,2),ordered_at DATETIME,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE order_items(id BIGINT PRIMARY KEY,order_id BIGINT,product_id BIGINT,product_name VARCHAR(255),quantity INT)");
        jdbc.execute("CREATE TABLE delivery_jobs(id BIGINT PRIMARY KEY,order_id BIGINT UNIQUE,status VARCHAR(30))");
        jdbc.execute("CREATE TABLE payments(id BIGINT PRIMARY KEY,order_id BIGINT,user_tariff_id BIGINT,provider VARCHAR(20),provider_transaction_id VARCHAR(80),status VARCHAR(20),payme_create_time BIGINT,payme_perform_time BIGINT,payme_cancel_time BIGINT,payme_reason INT,updated_at DATETIME)");
        jdbc.update("INSERT INTO products(id,name,stock_quantity,is_active) VALUES (1,'Baby food',10,1),(2,'Milk',0,1)");
        migrate();
        transactions=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(transactions);
        WarehouseService target=new WarehouseService(jdbc);
        ReflectionTestUtils.setField(target,"enabled",true);target.verifySchema();service=proxy(target);
    }
    void migrate() { new ResourceDatabasePopulator(new FileSystemResource("warehouse-step1.sql")).execute(ds); }
    long qty(long id) { return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Long.class,id); }
    long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_stock_movements",Long.class); }
    long receipt(int quantity) {
        long location=service.createLocation(Map.of("code","A-03","name","Shelf","type","SHELF"));
        return service.createReceipt(Map.of("supplier_name","Supplier","items",List.of(Map.of("product_id",1,"quantity",quantity,"location_id",location))));
    }
    @Test void draftDoesNotChangeStockAndConfirmationIsIdempotent() {
        long id=receipt(5);assertEquals(10,qty(1));assertEquals(2,count());
        service.postReceipt(id);service.postReceipt(id);
        assertEquals(15,qty(1));assertEquals(3,count());
        assertEquals("POSTED",service.receipt(id).get("status"));
    }
    @Test void reversalPreservesOriginalAndCannotRepeat() {
        long id=receipt(5);service.postReceipt(id);service.reverseReceipt(id,"Wrong invoice");service.reverseReceipt(id,"Retry");
        assertEquals(10,qty(1));assertEquals(4,count());
        assertEquals("REVERSED",service.receipt(id).get("status"));
        assertEquals("Wrong invoice",service.receipt(id).get("reversal_reason"));
    }
    @Test void reversalCannotCreateNegativeStock() {
        long id=receipt(12);service.postReceipt(id);
        tx.execute(status -> {jdbc.update("UPDATE products SET stock_quantity=1 WHERE id=1");service.recordChange(1,-21,"SALE","ORDER-1","SYSTEM","","test-sale");return null;});
        long history=count();
        assertEquals("WAREHOUSE_INSUFFICIENT_STOCK",assertThrows(WarehouseException.class,()->service.reverseReceipt(id,"Mistake")).getMessage());
        assertEquals(1,qty(1));assertEquals(history,count());assertEquals("POSTED",service.receipt(id).get("status"));
    }
    @Test void failureOnSecondLineRollsBackFirstLineAndHistory() {
        long location=service.createLocation(Map.of("code","A","name","Shelf","type","SHELF"));
        long id=service.createReceipt(Map.of("supplier_name","Supplier","items",List.of(
                Map.of("product_id",1,"quantity",3,"location_id",location),Map.of("product_id",2,"quantity",4,"location_id",location))));
        jdbc.update("UPDATE products SET is_active=0 WHERE id=2");
        assertThrows(WarehouseException.class,()->service.postReceipt(id));
        assertEquals(10,qty(1));assertEquals(0,qty(2));assertEquals(2,count());assertEquals("DRAFT",service.receipt(id).get("status"));
    }
    @Test void migrationCanRunAgainWithoutChangingBalances() {
        long id=receipt(5);service.postReceipt(id);migrate();
        assertEquals(15,qty(1));assertEquals(3,count());
    }
    @Test void simultaneousConfirmationsAddStockOnlyOnce() throws Exception {
        long id=receipt(5);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->service.postReceipt(id));var b=pool.submit(()->service.postReceipt(id));
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
            assertEquals(15,qty(1));assertEquals(3,count());
        } finally {pool.shutdownNow();}
    }
    @Test void invalidQuantityDuplicateProductAndEmptyReversalAreRejected() {
        assertThrows(WarehouseException.class,()->WarehouseService.positive(1.5));
        assertThrows(WarehouseException.class,()->WarehouseService.positive(-1));
        assertThrows(WarehouseException.class,()->service.reverseReceipt(receipt(1)," "));
        assertThrows(WarehouseException.class,()->service.requireDocumentStock(Map.of("stock_quantity",20)));
    }
    @Test void paidQueueExcludesUnpaidCancelledAndAcceptedOrders() {
        for(int i=1;i<=4;i++) jdbc.update("INSERT INTO orders VALUES (?,? ,? ,?,100,NOW(),NOW())",i,"ORDER-"+i,i==2?"PENDING":"PAID",i==3?"CANCELLED":"CONFIRMED");
        jdbc.update("INSERT INTO delivery_jobs VALUES (1,4,'ACCEPTED')");
        var result=service.preparation(1,20);
        assertEquals(1L,result.get("total"));
        assertEquals(1L,((Number)((Map<?,?>)((List<?>)result.get("items")).get(0)).get("id")).longValue());
    }
    IPaymeService payments() throws Exception {
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
        var config=new org.apache.ibatis.session.Configuration();config.getTypeAliasRegistry().registerAlias("camelMap",CamelCaseMap.class);
        factory.setConfiguration(config);factory.setMapperLocations(new FileSystemResource("src/main/resources/mapper/Payme/PaymeMapper.xml"));
        PaymeMapper mapper=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(PaymeMapper.class);
        PaymeService target=new PaymeService(new ObjectMapper(),mapper,service);return proxy(target);
    }
    PaymeRequest request(String method) {
        PaymeRequest request=new PaymeRequest();request.setMethod(method);request.setId(1);
        request.setParams(new ObjectMapper().valueToTree(Map.of("id","txn-1")));return request;
    }
    void orderPayment() {
        jdbc.update("INSERT INTO orders VALUES (1,'ORDER-1','PENDING','PENDING',100,NOW(),NOW())");
        jdbc.update("INSERT INTO order_items VALUES (1,1,1,'Baby food',2)");
        jdbc.update("INSERT INTO payments(id,order_id,provider,provider_transaction_id,status,payme_create_time) VALUES (1,1,'PAYME','txn-1','CREATED',1)");
    }
    @Test void paymeSaleAndRefundRetriesChangeStockOnlyOnce() throws Exception {
        orderPayment();IPaymeService payments=payments();
        assertNull(payments.handleRequest(request("PerformTransaction")).getError());
        assertNull(payments.handleRequest(request("PerformTransaction")).getError());
        assertEquals(8,qty(1));assertEquals(3,count());
        assertNull(payments.handleRequest(request("CancelTransaction")).getError());
        assertNull(payments.handleRequest(request("CancelTransaction")).getError());
        assertEquals(10,qty(1));assertEquals(4,count());
    }
    @Test void simultaneousPaymeCallbacksCannotDeductTwice() throws Exception {
        orderPayment();IPaymeService payments=payments();ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->payments.handleRequest(request("PerformTransaction")));
            var b=pool.submit(()->payments.handleRequest(request("PerformTransaction")));
            assertNull(a.get(10,TimeUnit.SECONDS).getError());assertNull(b.get(10,TimeUnit.SECONDS).getError());
            assertEquals(8,qty(1));assertEquals(3,count());
        } finally {pool.shutdownNow();}
    }
    @Test void insufficientSecondProductRollsBackWholePaymentAndLedger() throws Exception {
        orderPayment();jdbc.update("INSERT INTO order_items VALUES (2,1,2,'Milk',1)");
        assertNotNull(payments().handleRequest(request("PerformTransaction")).getError());
        assertEquals(10,qty(1));assertEquals(0,qty(2));assertEquals(2,count());
        assertEquals("CREATED",jdbc.queryForObject("SELECT status FROM payments WHERE id=1",String.class));
    }
}
