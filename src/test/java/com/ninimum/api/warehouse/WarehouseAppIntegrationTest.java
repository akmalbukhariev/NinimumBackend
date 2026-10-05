package com.ninimum.api.warehouse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="warehouse.test.jdbc",matches=".*")
class WarehouseAppIntegrationTest {
    JdbcTemplate jdbc;WarehouseAppService app;TransactionTemplate tx;
    @BeforeEach void setup() {
        String url=System.getProperty("warehouse.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/warehouse_test")) throw new IllegalArgumentException("Isolated loopback warehouse_test only");
        var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("warehouse_preparation_items","warehouse_preparation_events","warehouse_preparations","warehouse_app_sessions","warehouse_workers","warehouse_app_schema","order_items","orders","delivery_jobs","products")) jdbc.execute("DROP TABLE IF EXISTS "+t);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,barcode VARCHAR(120),stock_quantity INT)");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,order_number VARCHAR(100),payment_status VARCHAR(20),status VARCHAR(30),ordered_at DATETIME,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE order_items(id BIGINT PRIMARY KEY,order_id BIGINT,product_id BIGINT,product_name VARCHAR(255),quantity INT,product_image_url VARCHAR(1000))");
        jdbc.execute("CREATE TABLE delivery_jobs(id BIGINT PRIMARY KEY,order_id BIGINT UNIQUE,status VARCHAR(30))");
        jdbc.update("INSERT INTO products VALUES (1,'123456',10),(2,NULL,7)");
        jdbc.update("INSERT INTO orders VALUES (1,'ORDER1','PAID','CONFIRMED',NOW(),NOW()),(2,'ORDER2','PENDING','CONFIRMED',NOW(),NOW())");
        jdbc.update("INSERT INTO order_items(id,order_id,product_id,product_name,quantity) VALUES (1,1,1,'Milk',2),(2,1,1,'Milk',1),(3,1,2,'Bread',1)");
        jdbc.update("INSERT INTO delivery_jobs VALUES (1,1,'WAITING_ASSIGNMENT')");
        new ResourceDatabasePopulator(new FileSystemResource("warehouse-step2.sql")).execute(ds);
        var warehouse=org.mockito.Mockito.mock(WarehouseService.class);org.mockito.Mockito.when(warehouse.isEnabled()).thenReturn(true);
        var target=new WarehouseAppService(jdbc,PasswordEncoderFactories.createDelegatingPasswordEncoder(),warehouse);
        ReflectionTestUtils.setField(target,"enabled",true);target.verifySchema();
        var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));app=(WarehouseAppService)factory.getProxy();
        app.createWorker(Map.of("worker_code","WH001","full_name","Worker one","password","sample-password"));
        app.createWorker(Map.of("worker_code","WH002","full_name","Worker two","password","sample-password"));
    }
    void checkMilk(int qty) {app.check(1,"WH001",Map.of("product_id",1,"quantity",qty,"barcode","123456"));}
    void complete() {app.claim(1,"WH001");checkMilk(3);app.check(1,"WH001",Map.of("product_id",2,"quantity",1,"reason","No barcode on this product"));app.ready(1,"WH001");}
    void fails(String code,Runnable work) {var ex=assertThrows(WarehouseException.class,work::run);assertEquals(code,ex.getMessage());}
    @Test void cannotClaimUnpaidOrder() {fails("WAREHOUSE_ORDER_UNAVAILABLE",()->app.claim(2,"WH001"));}
    @Test void onlyOneWorkerCanClaimEvenConcurrently() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(String worker:List.of("WH001","WH002")) futures.add(pool.submit(()->{start.await();try{app.claim(1,worker);return true;}catch(WarehouseException ex){assertEquals("WAREHOUSE_ALREADY_CLAIMED",ex.getMessage());return false;}}));
            start.countDown();int successes=0;for(var f:futures) if(f.get(10,TimeUnit.SECONDS))successes++;assertEquals(1,successes);
        } finally {pool.shutdownNow();}
    }
    @Test void productImagesRemainAvailableBeforeAndAfterClaim() {
        jdbc.update("UPDATE order_items SET product_image_url='/uploads/milk.jpg' WHERE product_id=1");
        assertProductImages();
        app.claim(1,"WH001");
        assertProductImages();
    }
    private void assertProductImages() {
        var items = (List<Map<String,Object>>) app.detail(1).get("items");
        assertEquals("/uploads/milk.jpg", items.get(0).get("product_image_url"));
        assertNull(items.get(1).get("product_image_url"));
    }
    @Test void duplicateClaimAndAbsoluteCheckAreSafe() {
        app.claim(1,"WH001");app.claim(1,"WH001");checkMilk(3);checkMilk(3);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_items",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_events",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT checked_quantity FROM warehouse_preparation_items WHERE product_id=1",Integer.class));
        assertEquals(10,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Integer.class));
    }
    @Test void barcodeQuantityAndOwnerAreEnforced() {
        app.claim(1,"WH001");
        fails("WAREHOUSE_BARCODE_MISMATCH",()->app.check(1,"WH001",Map.of("product_id",1,"quantity",1,"barcode","999")));
        fails("WAREHOUSE_TOO_MANY",()->checkMilk(4));
        fails("WAREHOUSE_INVALID_INPUT",()->app.check(1,"WH001",Map.of("product_id",1,"quantity",1.5,"barcode","123456")));
        fails("WAREHOUSE_NOT_OWNER",()->app.check(1,"WH002",Map.of("product_id",1,"quantity",1,"barcode","123456")));
        fails("WAREHOUSE_INVALID_INPUT",()->app.check(1,"WH001",Map.of("product_id",2,"quantity",1)));
        fails("WAREHOUSE_INCOMPLETE",()->app.ready(1,"WH001"));
    }
    @Test void completedOrderIsReadyOnceAndDoesNotConsumeStockAgain() {
        complete();app.ready(1,"WH001");
        assertTrue(app.canDeliver(1));assertEquals("PREPARING",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_events WHERE kind='READY'",Integer.class));
        assertEquals(10,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1",Integer.class));
        tx.executeWithoutResult(s->app.requireReadyJob(1));
    }
    @Test void deliveryGateBlocksUnpackedAndAllowsExistingAcceptedRoutes() {
        fails("WAREHOUSE_NOT_READY",()->tx.executeWithoutResult(s->app.requireReadyJob(1)));
        jdbc.update("UPDATE delivery_jobs SET status='ACCEPTED' WHERE id=1");
        tx.executeWithoutResult(s->app.requireReadyJob(1));
        fails("WAREHOUSE_ORDER_UNAVAILABLE",()->app.claim(1,"WH001"));
    }
    @Test void blockedOrderCannotBecomeReadyUntilResolvedAndCanBeReset() {
        app.claim(1,"WH001");checkMilk(3);app.problem(1,"WH001",Map.of("reason","Missing item"));
        fails("WAREHOUSE_INVALID_STATE",()->app.ready(1,"WH001"));app.resume(1,"WH001");
        app.reset(1,"owner","New worker needed");app.claim(1,"WH002");
        assertEquals(0,jdbc.queryForObject("SELECT SUM(checked_quantity) FROM warehouse_preparation_items",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_events WHERE kind='RESET'",Integer.class));
    }
    @Test void cancellationAndRefundStopPackingAndCourierClaim() {
        app.claim(1,"WH001");jdbc.update("UPDATE orders SET payment_status='REFUNDED' WHERE id=1");
        fails("WAREHOUSE_ORDER_UNAVAILABLE",()->checkMilk(1));
        fails("WAREHOUSE_ORDER_UNAVAILABLE",()->tx.executeWithoutResult(s->app.requireReadyJob(1)));
    }
    @Test void loginReplacesOldSessionAndDeactivationRevokesIt() {
        app.login("wh001","sample-password",c->"token1");assertTrue(app.isCurrent("WH001","token1"));
        app.login("WH001","sample-password",c->"token2");assertFalse(app.isCurrent("WH001","token1"));assertTrue(app.isCurrent("WH001","token2"));
        long id=jdbc.queryForObject("SELECT id FROM warehouse_workers WHERE worker_code='WH001'",Long.class);
        app.updateWorker(id,Map.of("status","INACTIVE"));assertFalse(app.isCurrent("WH001","token2"));
        fails("WAREHOUSE_LOGIN_FAILED",()->app.login("WH001","sample-password",c->"token3"));
    }
    @Test void queueMineHistoryAndMigrationAreConsistent() {
        assertEquals(1L,app.orders("WH001","queue",1,20,false).get("total"));
        app.claim(1,"WH001");assertEquals(0L,app.orders("WH002","queue",1,20,false).get("total"));
        assertEquals(1L,app.orders("WH001","mine",1,20,false).get("total"));
        checkMilk(3);app.check(1,"WH001",Map.of("product_id",2,"quantity",1,"reason","No barcode"));app.ready(1,"WH001");
        assertEquals(1L,app.orders("WH001","history",1,20,false).get("total"));
        new ResourceDatabasePopulator(new FileSystemResource("warehouse-step2.sql")).execute(jdbc.getDataSource());assertTrue(app.canDeliver(1));
    }
    @Test void courierMustRecheckReadyAfterConcurrentAdminReset() throws Exception {
        complete();
        var locked=new CountDownLatch(1);var finishReset=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var reset=pool.submit(()->tx.executeWithoutResult(t->{
                jdbc.queryForObject("SELECT id FROM orders WHERE id=1 FOR UPDATE",Long.class);
                app.reset(1,"owner","Recheck packing");locked.countDown();
                try {if(!finishReset.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}
            }));
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            var claimed=pool.submit(()->{try {tx.executeWithoutResult(t->app.requireReadyJob(1));return true;}catch(WarehouseException e){assertEquals("WAREHOUSE_NOT_READY",e.getMessage());return false;}});
            finishReset.countDown();reset.get(10,TimeUnit.SECONDS);assertFalse(claimed.get(10,TimeUnit.SECONDS));
        } finally {finishReset.countDown();pool.shutdownNow();}
    }

}
