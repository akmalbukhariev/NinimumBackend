package com.ninimum.api.ordersafety;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="timers.test.jdbc",matches=".*")
class OrderTimersIntegrationTest {
    JdbcTemplate jdbc;
    OrderTimersService service;
    DataSourceTransactionManager manager;
    final Instant now=Instant.parse("2026-10-11T05:00:00Z");
    @BeforeEach void setup() throws Exception {
        String url=System.getProperty("timers.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/timers_test")) throw new IllegalArgumentException("Dedicated local database only");
        var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        for(String table:new String[]{"courier_dispatch_history","courier_dispatch_offers","courier_dispatch_settings","delivery_partial_return_items","delivery_partial_returns","order_timer_settings","order_problem_alerts","order_problem_events","order_status_history","orders","payments","delivery_jobs","warehouse_preparations","product_questions","products","delivery_app_workers"}) jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,status VARCHAR(30),payment_status VARCHAR(30),ordered_at DATETIME,updated_at DATETIME,cancelled_at DATETIME,cancel_reason TEXT,status_actor TEXT,status_source TEXT,status_reason TEXT)");
        jdbc.execute("CREATE TABLE order_status_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT,to_status VARCHAR(30),changed_at DATETIME,actor TEXT,source TEXT,reason TEXT)");
        jdbc.execute("CREATE TRIGGER status_audit AFTER UPDATE ON orders FOR EACH ROW BEGIN IF NOT(OLD.status <=> NEW.status) THEN INSERT INTO order_status_history(order_id,to_status,changed_at,actor,source,reason) VALUES(NEW.id,NEW.status,UTC_TIMESTAMP(),NEW.status_actor,NEW.status_source,NEW.status_reason); END IF; END");
        jdbc.execute("CREATE TABLE payments(id BIGINT PRIMARY KEY,order_id BIGINT,status VARCHAR(30))");
        jdbc.execute("CREATE TABLE delivery_jobs(id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,order_id BIGINT PRIMARY KEY,status VARCHAR(30),delivery_worker_id BIGINT,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE warehouse_preparations(order_id BIGINT PRIMARY KEY,worker_code VARCHAR(40))");
        jdbc.execute("CREATE TABLE delivery_app_workers(id BIGINT PRIMARY KEY,full_name VARCHAR(100),worker_code VARCHAR(50))");
        jdbc.execute("CREATE TABLE product_questions(id BIGINT PRIMARY KEY,status VARCHAR(30),answer TEXT,is_active INT,created_at DATETIME)");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,stock_quantity INT)");jdbc.update("INSERT INTO products VALUES(12,4)");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource("src/test/resources/live-schema-fixtures/order-timers.sql")).execute(ds);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource("database/migrations/20261017_complaints.sql")).execute(ds);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource("database/migrations/20261012_partial_returns.sql")).execute(ds);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource("database/migrations/20261017_courier_dispatch.sql")).execute(ds);
        service=new OrderTimersService(jdbc,manager);
    }
    void order(long id,String status,String payment,int age) {
        var since=OrderTimersService.timestamp(now.minusSeconds(age*60L));
        jdbc.update("INSERT INTO orders(id,status,payment_status,ordered_at,updated_at) VALUES(?,?,?,?,?)",id,status,payment,since,since);
        jdbc.update("INSERT INTO order_status_history(order_id,to_status,changed_at) VALUES(?,?,?)",id,status,since);
    }
    @Test void expiryIsOptInAndNeverChangesPaidOrdersOrStock() {
        order(1,"PENDING","PENDING",31);order(2,"CONFIRMED","PAID",120);
        jdbc.update("INSERT INTO delivery_jobs(order_id,status) VALUES(1,'WAITING_ASSIGNMENT')");
        service.scan(now);assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
        jdbc.update("UPDATE order_timer_settings SET expiry_enabled=1 WHERE id=1");service.scan(now);service.scan(now);
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM delivery_jobs WHERE order_id=1",String.class));
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM orders WHERE id=2",String.class));
        assertEquals("PENDING",jdbc.queryForObject("SELECT payment_status FROM orders WHERE id=1",String.class));
        assertEquals(4,jdbc.queryForObject("SELECT stock_quantity FROM products",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events WHERE kind='UNPAID_EXPIRED'",Integer.class));
        assertEquals("AUTO_EXPIRE",jdbc.queryForObject("SELECT source FROM order_status_history WHERE order_id=1 ORDER BY id DESC LIMIT 1",String.class));
    }
    @Test void alertsDeduplicateAndResolveWithPersistentHistory() {
        order(1,"CONFIRMED","PAID",31);order(2,"PREPARING","PAID",40);order(3,"READY","PAID",31);order(4,"ON_THE_WAY","PAID",121);order(5,"RETURNING","PAID",1);
        jdbc.update("INSERT INTO warehouse_preparations VALUES(2,'WH001')");jdbc.update("INSERT INTO delivery_jobs(order_id,status,delivery_worker_id) VALUES(4,'ON_THE_WAY',9)");
        jdbc.update("INSERT INTO product_questions(id,status,answer,is_active,created_at) VALUES(7,'NEW',NULL,1,?)",OrderTimersService.timestamp(now.minusSeconds(25*3600)));
        service.scan(now);service.scan(now.plusSeconds(60));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts WHERE resolved_at IS NULL",Integer.class));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events WHERE event='OPENED'",Integer.class));
        assertEquals("WH001",jdbc.queryForObject("SELECT responsible FROM order_problem_alerts WHERE entity_id=2",String.class));
        jdbc.update("UPDATE orders SET status='DELIVERED' WHERE id IN(1,2,3,4)");jdbc.update("UPDATE orders SET payment_status='REFUNDED' WHERE id=5");
        jdbc.update("UPDATE product_questions SET answer='Done',status='ANSWERED' WHERE id=7");service.scan(now.plusSeconds(120));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts WHERE resolved_at IS NULL",Integer.class));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events WHERE event='RESOLVED'",Integer.class));
        assertEquals(12,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events",Integer.class));
    }
    @Test void boundaryAndWorkingHoursAreRespected() {
        jdbc.update("UPDATE order_timer_settings SET expiry_enabled=1,working_start='09:00',working_end='18:00',working_days=31 WHERE id=1");
        order(1,"PENDING","PENDING",2000);order(2,"CONFIRMED","PAID",2000);
        // Sunday is closed: orders created during the weekend must not expire or alert.
        service.scan(now);assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts",Integer.class));
        jdbc.update("UPDATE order_timer_settings SET working_start='00:00',working_end='24:00',working_days=127 WHERE id=1");
        jdbc.update("UPDATE orders SET ordered_at=? WHERE id=1",OrderTimersService.timestamp(now.minusSeconds(29*60+59)));
        service.scan(now);assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
        service.scan(now.plusSeconds(1));assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
    }
    @Test void concurrentPaymentWinsWithoutBeingExpired() throws Exception {
        order(1,"PENDING","PENDING",31);jdbc.update("INSERT INTO payments VALUES(1,1,'CREATED')");jdbc.update("UPDATE order_timer_settings SET expiry_enabled=1 WHERE id=1");
        var locked=new CountDownLatch(1);var allowCommit=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var paid=executor.submit(()->new TransactionTemplate(manager).executeWithoutResult(tx->{
                jdbc.queryForMap("SELECT id FROM payments WHERE id=1 FOR UPDATE");jdbc.queryForMap("SELECT id FROM orders WHERE id=1 FOR UPDATE");locked.countDown();
                try{if(!allowCommit.await(5,TimeUnit.SECONDS))throw new RuntimeException("timeout");}catch(InterruptedException e){throw new RuntimeException(e);}
                jdbc.update("UPDATE payments SET status='PAID' WHERE id=1");jdbc.update("UPDATE orders SET payment_status='PAID',status='CONFIRMED' WHERE id=1");
            }));
            assertTrue(locked.await(5,TimeUnit.SECONDS));var expiry=executor.submit(()->service.scan(now));
            allowCommit.countDown();paid.get(5,TimeUnit.SECONDS);expiry.get(5,TimeUnit.SECONDS);
            assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events WHERE kind='UNPAID_EXPIRED'",Integer.class));
        } finally {allowCommit.countDown();executor.shutdownNow();}
    }
    @Test void eventFailureRollsBackCancellationAndDeliveryClosure() {
        order(1,"PENDING","PENDING",31);jdbc.update("INSERT INTO delivery_jobs(order_id,status) VALUES(1,'WAITING_ASSIGNMENT')");jdbc.update("UPDATE order_timer_settings SET expiry_enabled=1 WHERE id=1");
        jdbc.execute("CREATE TRIGGER fail_timer_event BEFORE INSERT ON order_problem_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        assertThrows(RuntimeException.class,()->service.scan(now));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class));
        assertEquals("WAITING_ASSIGNMENT",jdbc.queryForObject("SELECT status FROM delivery_jobs WHERE order_id=1",String.class));
    }
    @Test void complaintDeadlineIsIndependentAndReclassificationResolvesAlert() {
        jdbc.update("UPDATE order_timer_settings SET complaint_minutes=30,question_minutes=120 WHERE id=1");
        var created=OrderTimersService.timestamp(now.minusSeconds(60*60));
        jdbc.update("INSERT INTO product_questions(id,status,is_active,created_at,kind) VALUES(1,'NEW',1,?,'COMPLAINT'),(2,'NEW',1,?,'QUESTION')",created,created);
        service.scan(now);service.scan(now.plusSeconds(1));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts WHERE resolved_at IS NULL",Integer.class));
        assertEquals(1L,jdbc.queryForObject("SELECT entity_id FROM order_problem_alerts WHERE resolved_at IS NULL",Long.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events WHERE event='OPENED'",Integer.class));
        jdbc.update("UPDATE product_questions SET kind='QUESTION' WHERE id=1");service.scan(now.plusSeconds(2));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_alerts WHERE resolved_at IS NULL",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_problem_events WHERE event='RESOLVED'",Integer.class));
    }
}
