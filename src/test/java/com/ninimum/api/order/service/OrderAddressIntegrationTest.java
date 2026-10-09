package com.ninimum.api.order.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.deliveryapp.service.DeliveryAppMapper;
import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.param.CreateOrderParam;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Uses only a dedicated loopback database; never point this fixture at the live server. */
@EnabledIfSystemProperty(named="address.test.jdbc", matches=".*")
class OrderAddressIntegrationTest {
    JdbcTemplate jdbc;
    OrderMapper orders;
    DeliveryAppMapper deliveries;
    AdminManagementMapper admin;

    @BeforeEach void setup() throws Exception {
        String url = System.getProperty("address.test.jdbc");
        if (!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/address_test"))
            throw new IllegalArgumentException("Only isolated loopback address_test is permitted");
        var ds = new DriverManagerDataSource(url,"root","");
        jdbc = new JdbcTemplate(ds);
        for (String table : new String[]{"warehouse_preparation_items","warehouse_preparation_events","warehouse_workers","delivery_app_workers","delivery_job_tracking","products","warehouse_preparations","order_items","delivery_jobs","order_status_history","orders","user_addresses","users"})
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,first_name VARCHAR(100),last_name VARCHAR(100),phone_number VARCHAR(50),address TEXT,location_latitude DOUBLE,location_longitude DOUBLE)");
        jdbc.execute(Files.readString(Path.of("database/migrations/20261009_user_addresses.sql")));
        // This follow-up migration must also be safe for installations that already have the table.
        jdbc.execute(Files.readString(Path.of("database/migrations/20261009_user_addresses.sql")));
        jdbc.execute("CREATE TABLE orders(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,address_id BIGINT,user_tariff_id BIGINT,order_number VARCHAR(100),status VARCHAR(30),payment_status VARCHAR(30),subtotal_price INT,delivery_price INT,discount_price INT,total_price INT,ordered_at DATETIME,created_at DATETIME,updated_at DATETIME,cancelled_at DATETIME,cancel_reason TEXT,delivered_at DATETIME,is_history_deleted INT DEFAULT 0)");
        jdbc.execute(Files.readString(Path.of("database/migrations/20261008_order_delivery_address.sql")));
        String auditSql=Files.readString(Path.of("database/migrations/20261009_order_status_history.sql"));
        String[] auditParts=auditSql.split("DELIMITER \\$\\$");
        for(String statement:auditParts[0].split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        for(String statement:auditParts[1].replace("DELIMITER ;", "").split("\\$\\$"))
            if(!statement.isBlank()) jdbc.execute(statement);

        jdbc.execute("CREATE TABLE delivery_jobs(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT UNIQUE,customer_name VARCHAR(255),customer_phone VARCHAR(50),delivery_address TEXT,status VARCHAR(30),delivery_worker_id BIGINT,created_at DATETIME,updated_at DATETIME,accepted_at DATETIME,on_the_way_at DATETIME,delivered_at DATETIME,note TEXT)");
        jdbc.execute("CREATE TABLE order_items(id BIGINT PRIMARY KEY,order_id BIGINT,quantity INT)");
        jdbc.update("INSERT INTO users VALUES (7,'Customer','Test','+998900000000','Profile address',39.0500,66.8300)");
        var config = new org.apache.ibatis.session.Configuration();
        config.getTypeAliasRegistry().registerAlias("camelMap", CamelCaseMap.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds); factory.setConfiguration(config);
        factory.setMapperLocations(new FileSystemResource("src/main/resources/mapper/Order/OrderMapper.xml"),
                new FileSystemResource("src/main/resources/mapper/DeliveryApp/DeliveryAppMapper.xml"),
                new FileSystemResource("src/main/resources/mapper/AdminManagement/AdminManagementMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        orders = session.getMapper(OrderMapper.class);
        deliveries = session.getMapper(DeliveryAppMapper.class);
        admin = session.getMapper(AdminManagementMapper.class);
    }
    @Test void failedDeliveryReturnsOrderAndAuditsReasonAtomically() throws Exception {
        jdbc.execute("CREATE TABLE delivery_app_workers(id BIGINT PRIMARY KEY,worker_code VARCHAR(50),full_name VARCHAR(100),phone_number VARCHAR(50),status VARCHAR(30),is_online INT,vehicle_type VARCHAR(30),vehicle_number VARCHAR(30))");
        jdbc.update("INSERT INTO delivery_app_workers VALUES(9,'DEL001','Courier','123','ACTIVE',1,NULL,NULL)");
        var p=new CreateOrderParam();p.setUserId(7L);p.setDeliveryAddress("Shahrisabz");p.setSubtotalPrice(1500);p.setTotalPrice(1500);
        orders.createOrderAddress(p);orders.createOrder(p);long id=p.getOrderId();
        jdbc.update("UPDATE orders SET status='ON_THE_WAY',payment_status='PAID' WHERE id=?",id);
        deliveries.syncPaidOrders();long job=jdbc.queryForObject("SELECT id FROM delivery_jobs WHERE order_id=?",Long.class,id);
        jdbc.update("UPDATE delivery_jobs SET status='ON_THE_WAY',delivery_worker_id=9 WHERE id=?",job);
        var courier=new com.ninimum.api.deliveryapp.service.impl.DeliveryAppService(deliveries,
            org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            org.mockito.Mockito.mock(com.ninimum.api.warehouse.WarehouseAppService.class));
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        var request=new com.ninimum.api.param.DeliveryAppStatusParam();request.setJobId(job);request.setStatus("FAILED");request.setNote("   ");
        assertThrows(Exception.class,()->courier.updateStatus("DEL001",request));
        assertEquals("ON_THE_WAY",admin.getOrder(id).get("status"));
        request.setNote("Customer unavailable");
        // Force a later write to fail, proving job, order and trigger history roll back together.
        jdbc.execute("DROP TABLE IF EXISTS delivery_job_tracking");
        int before=admin.getOrderStatusHistory(id).size();
        assertThrows(RuntimeException.class,()->tx.executeWithoutResult(t->{try{courier.updateStatus("DEL001",request);}catch(Exception e){throw new RuntimeException(e);}}));
        assertEquals("ON_THE_WAY",admin.getOrder(id).get("status"));
        assertEquals("ON_THE_WAY",jdbc.queryForObject("SELECT status FROM delivery_jobs WHERE id=?",String.class,job));
        assertEquals(before,admin.getOrderStatusHistory(id).size());
        jdbc.execute("CREATE TABLE delivery_job_tracking(id BIGINT AUTO_INCREMENT PRIMARY KEY,delivery_job_id BIGINT,delivery_worker_id BIGINT,status VARCHAR(30),message TEXT,created_at DATETIME)");
        tx.executeWithoutResult(t->{try{courier.updateStatus("DEL001",request);}catch(Exception e){throw new RuntimeException(e);}});
        assertEquals("RETURNING",admin.getOrder(id).get("status"));
        assertEquals("PAID",admin.getOrder(id).get("payment_status"));
        var history=admin.getOrderStatusHistory(id);assertEquals(before+1,history.size());
        assertEquals("COURIER:9",history.get(0).get("actor"));assertEquals("Customer unavailable",history.get(0).get("reason"));
        assertEquals("ON_THE_WAY",history.get(0).get("from_status"));assertEquals("RETURNING",history.get(0).get("to_status"));
        assertEquals(1,deliveries.getActiveJobs(9L).size());assertEquals(0,deliveries.getHistoryJobs(9L).size());
        assertEquals(0,deliveries.syncOrderStatusFromJob(job));assertEquals(0,admin.syncOrderFromDeliveryJob(job));
        assertThrows(Exception.class,()->courier.updateStatus("DEL001",request));
        assertEquals(before+1,admin.getOrderStatusHistory(id).size());
        var query=new com.ninimum.api.param.OrderListParam();query.setUserId(7L);
        assertEquals("RETURNING",orders.getOrderList(query).get(0).getStatus());
        // Repair is idempotent and logs the present repair, without changing payment.
        jdbc.update("UPDATE orders SET status='ON_THE_WAY' WHERE id=?",id);
        jdbc.execute(Files.readString(Path.of("database/migrations/20261009_order_returning.sql")));
        int repaired=admin.getOrderStatusHistory(id).size();
        jdbc.execute(Files.readString(Path.of("database/migrations/20261009_order_returning.sql")));
        assertEquals(repaired,admin.getOrderStatusHistory(id).size());assertEquals("RETURN_REPAIR",admin.getOrderStatusHistory(id).get(0).get("source"));
        assertEquals("RETURNING",admin.getOrder(id).get("status"));assertEquals("PAID",admin.getOrder(id).get("payment_status"));
    }
    @Test void historyRecordsIdentityReasonAndRollsBackWithStatus() throws Exception {
        var p=new CreateOrderParam();p.setUserId(7L);p.setDeliveryAddress("Shahrisabz");
        p.setSubtotalPrice(1000);p.setTotalPrice(1000);
        orders.createOrderAddress(p);orders.createOrder(p);
        jdbc.update("UPDATE orders SET status='CONFIRMED',status_actor='PAYME',status_source='PAYMENT' WHERE id=?",p.getOrderId());
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin-akmal","",java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try { assertEquals(1,admin.cancelOrder(p.getOrderId(),"Test cancellation")); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        var rows=admin.getOrderStatusHistory(p.getOrderId());
        assertEquals(3,rows.size());assertEquals("admin-akmal",rows.get(0).get("actor"));
        assertEquals("CONFIRMED",rows.get(0).get("from_status"));assertEquals("CANCELLED",rows.get(0).get("to_status"));
        assertEquals("Test cancellation",rows.get(0).get("reason"));
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=?",p.getOrderId());
        assertEquals(3,admin.getOrderStatusHistory(p.getOrderId()).size());
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(t->{jdbc.update("UPDATE orders SET status='CONFIRMED' WHERE id=?",p.getOrderId());t.setRollbackOnly();});
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,p.getOrderId()));
        assertEquals(3,admin.getOrderStatusHistory(p.getOrderId()).size());
        jdbc.update("UPDATE orders SET status='CONFIRMED' WHERE id=?",p.getOrderId());
        assertEquals("SYSTEM",admin.getOrderStatusHistory(p.getOrderId()).get(0).get("actor"));
    }

    @Test void threeDestinationsStayWithTheirOrdersAfterProfileChanges() throws Exception {
        long[] jobs = new long[3];
        long[] ids = new long[3];
        for (int i=0;i<3;i++) {
            var p = new CreateOrderParam();
            p.setUserId(7L); p.setDeliveryAddress("Shahrisabz street " + i);
            p.setDeliveryLatitude(39.0578 + i*0.01); p.setDeliveryLongitude(66.8342 + i*0.01);
            p.setSubtotalPrice(1000); p.setDiscountPrice(0); p.setTotalPrice(1000);
            assertEquals(1, orders.createOrderAddress(p));
            assertNotNull(p.getAddressId());
            assertEquals(7L,jdbc.queryForObject("SELECT user_id FROM user_addresses WHERE address_id=?",Long.class,p.getAddressId()));
            assertEquals(1, orders.createOrder(p));
            ids[i] = p.getOrderId();
            jdbc.update("UPDATE orders SET status='CONFIRMED',payment_status='PAID' WHERE id=?",p.getOrderId());
        }
        deliveries.syncPaidOrders();
        jdbc.update("UPDATE delivery_jobs SET delivery_worker_id=9,status='ACCEPTED'");
        jdbc.update("UPDATE users SET address='New profile address',location_latitude=39.12,location_longitude=66.90 WHERE id=7");
        for (int i=0;i<3;i++) {
            jobs[i] = jdbc.queryForObject("SELECT id FROM delivery_jobs WHERE order_id=?",Long.class,ids[i]);
            var job = deliveries.getJobDetail(jobs[i],9L);
            assertEquals("Shahrisabz street " + i, job.getDeliveryAddress());
            assertEquals(39.0578+i*0.01, job.getLocationLatitude(),0.0000001);
            assertEquals(66.8342+i*0.01, job.getLocationLongitude(),0.0000001);
            assertEquals("Shahrisabz street " + i, admin.getOrder(ids[i]).get("customer_address"));
        }
        assertEquals(3, deliveries.getActiveJobs(9L).size());
        // A different courier cannot read an assigned delivery's destination.
        assertNull(deliveries.getJobDetail(jobs[0],10L));
        // The admin's independent job creation path must also use the order snapshot.
        jdbc.execute("DELETE FROM delivery_jobs");
        admin.syncPaidOrders();
        for (int i=0;i<3;i++) {
            assertEquals("Shahrisabz street " + i,
                    jdbc.queryForObject("SELECT delivery_address FROM delivery_jobs WHERE order_id=?",String.class,ids[i]));
        }
    }
    @Test void cancelledOrderCannotBeRevivedByStaleDeliveryOrQueue() throws Exception {
        var p = new CreateOrderParam();
        p.setUserId(7L); p.setDeliveryAddress("Shahrisabz");
        p.setDeliveryLatitude(39.0578); p.setDeliveryLongitude(66.8342);
        p.setSubtotalPrice(1000); p.setDiscountPrice(0); p.setTotalPrice(1000);
        orders.createOrderAddress(p); orders.createOrder(p);
        long id=p.getOrderId();
        jdbc.update("UPDATE orders SET status='CONFIRMED',payment_status='PAID' WHERE id=?",id);
        deliveries.syncPaidOrders();
        long job=jdbc.queryForObject("SELECT id FROM delivery_jobs WHERE order_id=?",Long.class,id);
        jdbc.execute("CREATE TABLE warehouse_preparations(order_id BIGINT PRIMARY KEY,status VARCHAR(30),worker_code VARCHAR(50),note TEXT,ready_at DATETIME)");
        jdbc.update("INSERT INTO warehouse_preparations VALUES (?,'PICKING','WH001',NULL,NULL)",id);
        var warehouse = new com.ninimum.api.warehouse.WarehouseAppService(jdbc,
                org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                org.mockito.Mockito.mock(com.ninimum.api.warehouse.WarehouseService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(warehouse,"enabled",true);
        assertEquals(1L,warehouse.orders("WH001","mine",1,20,false).get("total"));
        assertEquals(1,admin.cancelOrder(id,"Customer cancelled"));
        admin.syncDeliveryJobFromOrder(id,"CANCELLED");
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM delivery_jobs WHERE id=?",String.class,job));
        assertEquals("PAID",jdbc.queryForObject("SELECT payment_status FROM orders WHERE id=?",String.class,id));
        assertEquals(0L,warehouse.orders("WH001","mine",1,20,false).get("total"));
        assertEquals(0L,warehouse.orders("WH001","queue",1,20,true).get("total"));
        // Simulate a historical mismatch: a stale courier record must not override cancellation.
        jdbc.update("UPDATE delivery_jobs SET status='ON_THE_WAY',delivery_worker_id=9 WHERE id=?",job);
        assertEquals(0,deliveries.syncOrderStatusFromJob(job));
        assertEquals(0,admin.syncOrderFromDeliveryJob(job));
        assertEquals(0,deliveries.updateOrderStatusForJob(job,"DELIVERED"));
        assertEquals(0,deliveries.updateJobStatus(job,9L,"DELIVERED",null));
        assertNull(deliveries.getOwnedJobStatus(job,9L));
        assertEquals(0,admin.updateDeliveryJob(new java.util.HashMap<>(java.util.Map.of("id",job,"status","DELIVERED"))));
        assertTrue(deliveries.getActiveJobs(9L).isEmpty());
        var query=new com.ninimum.api.param.OrderListParam();query.setUserId(7L);
        assertEquals("CANCELLED",orders.getOrderList(query).get(0).getStatus());
        jdbc.update("UPDATE delivery_jobs SET status='WAITING_ASSIGNMENT',delivery_worker_id=NULL WHERE id=?",job);
        assertEquals(0,deliveries.claimJob(job,10L));
    }

    @Test void preparationReadyAndCourierRouteHaveDistinctOrderStates() throws Exception {
        jdbc.execute("ALTER TABLE order_items ADD COLUMN product_id BIGINT, ADD COLUMN product_name VARCHAR(255), ADD COLUMN product_image_url VARCHAR(255), ADD COLUMN unit_price INT, ADD COLUMN total_price INT");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,barcode VARCHAR(120))");
        jdbc.execute("CREATE TABLE warehouse_workers(worker_code VARCHAR(50),full_name VARCHAR(100),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE warehouse_preparations(order_id BIGINT PRIMARY KEY,status VARCHAR(30),worker_code VARCHAR(50),note TEXT,started_at DATETIME,ready_at DATETIME,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE warehouse_preparation_items(order_id BIGINT,product_id BIGINT,product_name VARCHAR(255),barcode VARCHAR(120),required_quantity INT,checked_quantity INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE warehouse_preparation_events(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT,actor VARCHAR(50),kind VARCHAR(30),product_id BIGINT,quantity INT,note TEXT,created_at DATETIME)");
        jdbc.execute("CREATE TABLE delivery_app_workers(id BIGINT,worker_code VARCHAR(50),full_name VARCHAR(100),phone_number VARCHAR(50),status VARCHAR(20),is_online INT,vehicle_type VARCHAR(50),vehicle_number VARCHAR(50))");
        jdbc.execute("CREATE TABLE delivery_job_tracking(delivery_job_id BIGINT,delivery_worker_id BIGINT,status VARCHAR(30),message TEXT,created_at DATETIME)");
        jdbc.execute("ALTER TABLE orders MODIFY COLUMN status ENUM('PENDING','CONFIRMED','PREPARING','ON_THE_WAY','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING'");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new FileSystemResource("database/migrations/20261009_order_ready_status.sql")).execute(jdbc.getDataSource());
        jdbc.update("INSERT INTO products VALUES (12,'123456')");
        jdbc.update("INSERT INTO warehouse_workers VALUES ('WH001','Warehouse worker','ACTIVE')");
        jdbc.update("INSERT INTO delivery_app_workers VALUES (9,'CO001','Courier','+998900000000','ACTIVE',1,NULL,NULL)");
        var p=new CreateOrderParam();p.setUserId(7L);p.setDeliveryAddress("Shahrisabz");
        p.setDeliveryLatitude(39.0578);p.setDeliveryLongitude(66.8342);
        p.setSubtotalPrice(1000);p.setDiscountPrice(0);p.setTotalPrice(1000);
        orders.createOrderAddress(p);orders.createOrder(p);long id=p.getOrderId();
        jdbc.update("INSERT INTO order_items(id,order_id,quantity,product_id,product_name,unit_price,total_price) VALUES (1,?,1,12,'Milk',1000,1000)",id);
        jdbc.update("UPDATE orders SET status='CONFIRMED',payment_status='PAID' WHERE id=?",id);
        var warehouse=new com.ninimum.api.warehouse.WarehouseAppService(jdbc,
                org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                org.mockito.Mockito.mock(com.ninimum.api.warehouse.WarehouseService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(warehouse,"enabled",true);
        var courier=new com.ninimum.api.deliveryapp.service.impl.DeliveryAppService(deliveries,
                org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),warehouse);
        var tx=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        deliveries.syncPaidOrders();long job=jdbc.queryForObject("SELECT id FROM delivery_jobs WHERE order_id=?",Long.class,id);
        assertFalse(warehouse.canDeliver(id));
        assertEquals(0,admin.updateDeliveryJob(new java.util.HashMap<>(java.util.Map.of("id",job,"status","DELIVERED"))));
        tx.executeWithoutResult(t->warehouse.claim(id,"WH001"));
        assertEquals("PREPARING",admin.getOrder(id).get("status"));
        assertThrows(com.ninimum.api.warehouse.WarehouseException.class,()->tx.executeWithoutResult(t->warehouse.ready(id,"WH001")));
        assertEquals("PREPARING",admin.getOrder(id).get("status"));
        tx.executeWithoutResult(t->warehouse.check(id,"WH001",java.util.Map.of("product_id",12,"quantity",1,"barcode","123456")));
        tx.executeWithoutResult(t->warehouse.ready(id,"WH001"));
        assertEquals("READY",admin.getOrder(id).get("status"));assertTrue(warehouse.canDeliver(id));
        tx.executeWithoutResult(t->{try{courier.claimJob("CO001",job);}catch(Exception e){throw new RuntimeException(e);}});
        assertEquals("READY",admin.getOrder(id).get("status"));
        tx.executeWithoutResult(t->{try{var request=new com.ninimum.api.param.DeliveryAppStatusParam();request.setJobId(job);request.setStatus("ON_THE_WAY");courier.updateStatus("CO001",request);}catch(Exception e){throw new RuntimeException(e);}});
        assertEquals("ON_THE_WAY",admin.getOrder(id).get("status"));
        // A stale accepted job cannot move an order that is already on its route backwards.
        jdbc.update("UPDATE delivery_jobs SET status='ACCEPTED' WHERE id=?",job);
        assertEquals(0,deliveries.syncOrderStatusFromJob(job));
        assertEquals(0,admin.syncOrderFromDeliveryJob(job));
        assertEquals("ON_THE_WAY",admin.getOrder(id).get("status"));
        jdbc.update("UPDATE delivery_jobs SET status='ON_THE_WAY' WHERE id=?",job);
        tx.executeWithoutResult(t->{try{var request=new com.ninimum.api.param.DeliveryAppStatusParam();request.setJobId(job);request.setStatus("DELIVERED");courier.updateStatus("CO001",request);}catch(Exception e){throw new RuntimeException(e);}});
        assertEquals("DELIVERED",admin.getOrder(id).get("status"));
        var query=new com.ninimum.api.param.OrderListParam();query.setUserId(7L);
        assertEquals("DELIVERED",orders.getOrderList(query).get(0).getStatus());
        var history=admin.getOrderStatusHistory(id);
        assertEquals(java.util.List.of("DELIVERED","ON_THE_WAY","READY","PREPARING","CONFIRMED","PENDING"),
                history.stream().map(h->h.get("to_status")).collect(java.util.stream.Collectors.toList()));
        assertEquals("WH001",history.get(2).get("actor"));
        assertEquals("WH001",history.get(3).get("actor"));
        assertEquals("COURIER:9",history.get(0).get("actor"));
        assertEquals("COURIER:9",history.get(1).get("actor"));
        var ownerAuth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("owner-akmal","",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SUPER_ADMIN")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(ownerAuth);
        try {
            var correction=new com.ninimum.api.admin.management.service.OrderCorrectionService(jdbc);
            tx.executeWithoutResult(t->correction.correct(id,java.util.Map.of("expected_status","DELIVERED","status","ON_THE_WAY","reason","Wrong completion")));
            assertEquals("ON_THE_WAY",admin.getOrder(id).get("status"));
            assertNull(admin.getOrder(id).get("delivered_at"));
            assertEquals("ON_THE_WAY",jdbc.queryForObject("SELECT status FROM delivery_jobs WHERE id=?",String.class,job));
            assertEquals("owner-akmal",admin.getOrderStatusHistory(id).get(0).get("actor"));
            assertEquals("Wrong completion",admin.getOrderStatusHistory(id).get(0).get("reason"));
            // Failed validation keeps the status, courier assignment and audit unchanged.
            jdbc.update("UPDATE warehouse_preparation_items SET checked_quantity=0 WHERE order_id=?",id);
            int count=admin.getOrderStatusHistory(id).size();
            assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(t->correction.correct(id,java.util.Map.of("expected_status","ON_THE_WAY","status","READY","reason","Test"))));
            assertEquals("ON_THE_WAY",admin.getOrder(id).get("status"));assertEquals(count,admin.getOrderStatusHistory(id).size());
            jdbc.update("UPDATE warehouse_preparation_items SET checked_quantity=required_quantity WHERE order_id=?",id);
            tx.executeWithoutResult(t->correction.correct(id,java.util.Map.of("expected_status","ON_THE_WAY","status","READY","reason","Assign courier again")));
            assertEquals("READY",admin.getOrder(id).get("status"));
            assertNull(jdbc.queryForObject("SELECT delivery_worker_id FROM delivery_jobs WHERE id=?",Long.class,job));
            assertEquals("WAITING_ASSIGNMENT",jdbc.queryForObject("SELECT status FROM delivery_jobs WHERE id=?",String.class,job));
            tx.executeWithoutResult(t->correction.correct(id,java.util.Map.of("expected_status","READY","status","PREPARING","reason","Recheck products")));
            assertEquals("PREPARING",admin.getOrder(id).get("status"));assertFalse(warehouse.canDeliver(id));
            assertEquals(0,jdbc.queryForObject("SELECT SUM(checked_quantity) FROM warehouse_preparation_items WHERE order_id=?",Integer.class,id));
            tx.executeWithoutResult(t->correction.correct(id,java.util.Map.of("expected_status","PREPARING","status","CONFIRMED","reason","Restart preparation")));
            assertEquals("CONFIRMED",admin.getOrder(id).get("status"));
            assertEquals("PAID",admin.getOrder(id).get("payment_status"));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_preparation_items WHERE order_id=?",Integer.class,id));
            assertEquals("WAITING",jdbc.queryForObject("SELECT status FROM warehouse_preparations WHERE order_id=?",String.class,id));
            // The warehouse can claim this corrected order and prepare it normally again.
            tx.executeWithoutResult(t->warehouse.claim(id,"WH001"));
            assertEquals("PREPARING",admin.getOrder(id).get("status"));
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}


    }

}
