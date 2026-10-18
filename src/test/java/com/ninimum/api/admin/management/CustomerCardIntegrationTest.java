package com.ninimum.api.admin.management;

import com.ninimum.api.admin.management.service.CustomerCardService;
import com.ninimum.api.warehouse.WarehouseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="customer.test.jdbc",matches=".*")
class CustomerCardIntegrationTest {
    JdbcTemplate jdbc;CustomerCardService service;
    @BeforeEach void setup() {
        String url=System.getProperty("customer.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/customer_test"))throw new IllegalArgumentException("Dedicated local customer_test only");
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"root",""));service=new CustomerCardService(jdbc);
        for(String table:List.of("payments","user_tariffs","tariff_plans","orders","users"))jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,first_name VARCHAR(50),last_name VARCHAR(50),phone_number VARCHAR(30),email VARCHAR(100),address VARCHAR(255),is_active BOOLEAN,status VARCHAR(30),created_at DATETIME,password VARCHAR(100),reset_token VARCHAR(100))");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,user_id BIGINT,order_number VARCHAR(100),status VARCHAR(30),payment_status VARCHAR(30),total_price DECIMAL(16,2),delivery_address VARCHAR(255),ordered_at DATETIME,hidden_by_customer BOOLEAN)");
        jdbc.execute("CREATE TABLE payments(id BIGINT PRIMARY KEY,user_id BIGINT,order_id BIGINT,user_tariff_id BIGINT,provider VARCHAR(30),provider_transaction_id VARCHAR(100),amount DECIMAL(16,2),status VARCHAR(30),payme_perform_time BIGINT,payme_cancel_time BIGINT)");
        jdbc.execute("CREATE TABLE user_tariffs(id BIGINT PRIMARY KEY,user_id BIGINT,tariff_plan_id BIGINT,status VARCHAR(30),auto_renew BOOLEAN,start_date DATE,end_date DATE)");
        jdbc.execute("CREATE TABLE tariff_plans(id BIGINT PRIMARY KEY,name VARCHAR(100))");
        jdbc.update("INSERT INTO users VALUES(1,'Test','Customer','0012345','test@example.com','Current address',1,'ACTIVE','2026-10-01','secret','private'),(2,'Other','Customer','0099999',NULL,NULL,1,'ACTIVE','2026-10-01','other','other')");
    }
    @SuppressWarnings("unchecked") List<Map<String,Object>> items(Map<String,Object> response){return (List<Map<String,Object>>)response.get("items");}
    @Test void isolatesCustomersAndExcludesPrivateFieldsButRetainsHiddenOrderHistory() {
        jdbc.update("INSERT INTO orders VALUES(87,1,'ORD-87','DELIVERED','PAID',1000,' Old address ','2026-10-16',1),(88,2,'ORD-88','PENDING','PENDING',2000,'Other address','2026-10-17',0)");
        var response=service.get(1,"orders",1);assertEquals(1L,response.get("total"));assertEquals(87L,((Number)items(response).get(0).get("id")).longValue());
        var profile=(Map<?,?>)response.get("customer");assertFalse(profile.containsKey("password"));assertFalse(profile.containsKey("reset_token"));assertEquals("0012345",profile.get("phone_number"));
        assertEquals(0L,service.get(1,"payments",1).get("total"));
    }
    @Test void paymentAndSubscriptionSectionsUseExactCustomerId() {
        jdbc.update("INSERT INTO payments VALUES(1,1,87,NULL,'PAYME','receipt-one',1000,'PAID',1,0),(2,2,88,NULL,'PAYME','receipt-other',2000,'PAID',1,0),(3,1,NULL,10,'PAYME','tariff-payment',300,'PAID',1,0)");
        jdbc.update("INSERT INTO tariff_plans VALUES(1,'Monthly')");
        jdbc.update("INSERT INTO user_tariffs VALUES(10,1,1,'ACTIVE',0,'2026-10-01','2026-11-01'),(11,2,1,'ACTIVE',0,'2026-10-01','2026-11-01'),(12,1,999,'EXPIRED',0,'2026-09-01','2026-10-01')");
        assertEquals(2L,service.get(1,"payments",1).get("total"));assertTrue(items(service.get(1,"payments",1)).stream().noneMatch(p->"receipt-other".equals(p.get("provider_transaction_id"))));
        var subscriptions=service.get(1,"subscriptions",1);assertEquals(2L,subscriptions.get("total"));assertNull(items(subscriptions).get(0).get("tariff_name"));
    }
    @Test void groupsTrimmedAddressesAndDoesNotInventCurrentAddressHistory() {
        jdbc.update("INSERT INTO orders(id,user_id,delivery_address,ordered_at) VALUES(1,1,'  Old address  ','2026-10-01'),(2,1,'Old address','2026-10-16'),(3,1,'   ','2026-10-17'),(4,2,'Other address','2026-10-17')");
        var response=service.get(1,"addresses",1);assertEquals(1L,response.get("total"));assertEquals("Old address",items(response).get(0).get("address"));assertEquals(2L,((Number)items(response).get(0).get("order_count")).longValue());
    }
    @Test void pagesAreBoundedAndNonOverlapping() {
        for(int id=1;id<=25;id++)jdbc.update("INSERT INTO orders(id,user_id,ordered_at) VALUES(?,1,'2026-10-17')",id);
        var first=service.get(1,"orders",0);var second=service.get(1,"orders",2);
        assertEquals(25L,first.get("total"));assertEquals(20,items(first).size());assertEquals(5,items(second).size());
        assertEquals(25L,((Number)items(first).get(0).get("id")).longValue());assertEquals(5L,((Number)items(second).get(0).get("id")).longValue());assertTrue(items(service.get(1,"orders",Integer.MAX_VALUE)).isEmpty());
    }
    @Test void rejectsMissingCustomersAndUnknownSections() {
        assertThrows(WarehouseException.class,()->service.get(999,"orders",1));assertThrows(WarehouseException.class,()->service.get(-1,"orders",1));
        assertThrows(IllegalArgumentException.class,()->service.get(1,"orders; DROP TABLE users",1));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
    }
}
