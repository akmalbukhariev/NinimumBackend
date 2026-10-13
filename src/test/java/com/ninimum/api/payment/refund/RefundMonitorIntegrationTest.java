package com.ninimum.api.payment.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.payment.service.PaymeMapper;
import com.ninimum.api.payment.service.impl.PaymeService;
import com.ninimum.api.warehouse.WarehouseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="refund.test.jdbc",matches=".*")
class RefundMonitorIntegrationTest {
    JdbcTemplate jdbc;
    AdminManagementMapper admin;
    RefundMonitor monitor;
    @BeforeEach void setup() throws Exception {
        String url=System.getProperty("refund.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/refund_test")) throw new IllegalArgumentException("Dedicated local database only");
        var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);
        for(String table:new String[]{"payment_refund_checks","payments","order_status_history","orders","users","products"}) jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,first_name TEXT,last_name TEXT,phone_number TEXT,address TEXT)");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,user_id BIGINT,status VARCHAR(30),payment_status VARCHAR(30),cancelled_at DATETIME,delivery_address TEXT,status_actor TEXT,status_source TEXT,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE order_status_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT,to_status VARCHAR(30),changed_at DATETIME)");
        jdbc.execute("CREATE TABLE payments(id BIGINT PRIMARY KEY,provider VARCHAR(20),provider_transaction_id VARCHAR(100),order_id BIGINT,user_tariff_id BIGINT,amount INT,amount_tiyin BIGINT,status VARCHAR(30),payme_create_time BIGINT,payme_perform_time BIGINT,payme_cancel_time BIGINT,payme_reason INT,updated_at DATETIME)");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,stock_quantity INT)");
        jdbc.execute(Files.readString(Path.of("database/migrations/20261011_refund_monitor.sql")));
        jdbc.update("INSERT INTO orders(id,status,payment_status) VALUES(73,'RETURNING','PAID')");
        jdbc.update("INSERT INTO order_status_history(order_id,to_status,changed_at) VALUES(73,'RETURNING',UTC_TIMESTAMP()-INTERVAL 25 HOUR)");
        jdbc.update("INSERT INTO payments(id,provider,provider_transaction_id,order_id,amount,amount_tiyin,status,payme_perform_time) VALUES(9,'PAYME','receipt',73,1300,130000,'PAID',1000)");
        jdbc.update("INSERT INTO products VALUES(12,4)");
        var config=new org.apache.ibatis.session.Configuration();config.getTypeAliasRegistry().registerAlias("camelMap",CamelCaseMap.class);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        factory.setMapperLocations(new FileSystemResource("src/main/resources/mapper/Payme/PaymeMapper.xml"),new FileSystemResource("src/main/resources/mapper/AdminManagement/AdminManagementMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());admin=session.getMapper(AdminManagementMapper.class);
        var json=new ObjectMapper();var client=mock(PaymeReceiptClient.class);when(client.available()).thenReturn(true);
        when(client.receipt("receipt")).thenReturn(json.readTree("{\"_id\":\"receipt\",\"account\":{\"order_id\":73},\"state\":50,\"amount\":130000,\"pay_time\":1000,\"cancel_time\":2000}"));
        var payme=new PaymeService(json,session.getMapper(PaymeMapper.class),new WarehouseService(jdbc));
        monitor=new RefundMonitor(jdbc,client,payme,json,new DataSourceTransactionManager(ds));
    }
    @Test void refundReconciliationChangesPaymentOnceWithoutRestockingReturnedItems() {
        jdbc.update("UPDATE orders SET status='RETURNED' WHERE id=73");
        monitor.poll();monitor.poll();
        assertEquals("REFUNDED",admin.getOrder(73).get("payment_status"));
        assertEquals("RETURNED",admin.getOrder(73).get("status"));
        assertEquals(4,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=12",Integer.class));
        assertEquals(2000L,jdbc.queryForObject("SELECT payme_cancel_time FROM payments WHERE id=9",Long.class));
        assertEquals(50,((Number)admin.getOrderPayments(73).get(0).get("refund_receipt_state")).intValue());
        assertEquals(0,((Number)admin.getOrder(73).get("refund_pending")).intValue());
    }
    @Test void returnAgeSurvivesReceiptAndResetsAfterReopening() {
        String since=(String)admin.getOrder(73).get("refund_pending_since");
        assertTrue(java.time.Instant.parse(since).isBefore(java.time.Instant.now().minusSeconds(24*3600)));
        jdbc.update("UPDATE orders SET status='RETURNED' WHERE id=73");
        jdbc.update("INSERT INTO order_status_history(order_id,to_status,changed_at) VALUES(73,'RETURNED',UTC_TIMESTAMP())");
        assertEquals(since,admin.getOrder(73).get("refund_pending_since"));
        jdbc.update("INSERT INTO order_status_history(order_id,to_status,changed_at) VALUES(73,'ON_THE_WAY',UTC_TIMESTAMP())");
        jdbc.update("INSERT INTO order_status_history(order_id,to_status,changed_at) VALUES(73,'RETURNING',UTC_TIMESTAMP())");
        assertNotEquals(since,admin.getOrder(73).get("refund_pending_since"));
    }
    @Test void laterFailureRollsBackFinancialUpdate() {
        jdbc.execute("CREATE TRIGGER reject_monitor_metadata BEFORE INSERT ON payment_refund_checks FOR EACH ROW BEGIN IF NEW.receipt_state=50 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'; END IF; END");
        monitor.poll();
        assertEquals("PAID",admin.getOrder(73).get("payment_status"));
        assertEquals("PAID",admin.getOrderPayments(73).get(0).get("status"));
        assertEquals(4,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=12",Integer.class));
        assertEquals("CHECK_FAILED",admin.getOrderPayments(73).get(0).get("refund_check_error"));
    }
}
