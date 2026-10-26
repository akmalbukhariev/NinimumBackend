package com.ninimum.api.taxi;

import com.ninimum.api.warehouse.WarehouseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Taxi delivery on the real schema and status rules (reports_test: audit schema + all migrations from 20261017). */
@EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*")
class TaxiDeliveryIntegrationTest {
    JdbcTemplate jdbc;TaxiDelivery taxi;
    Map<String,Object> ride=Map.of("company","Yandex Go","driver_name","Ali","driver_phone","+998901234567","car_number","10 A 123 BC","quoted_fare",20000);

    @BeforeEach void setup(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("taxi_deliveries","order_status_history","orders","order_timer_settings"))jdbc.execute("DELETE FROM "+t);
        jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price,delivery_method) VALUES(1,1,'T1','READY','PAID',5000,5000,'TAXI'),(2,1,'T2','READY','PAID',5000,5000,'TAXI'),(3,1,'C3','READY','PAID',5000,5000,'COURIER'),(4,1,'T4','PREPARING','PAID',5000,5000,'TAXI')");
        taxi=new TaxiDelivery(jdbc);
    }

    @Test void bookThenDeliver(){
        assertEquals("WAREHOUSE_INVALID_INPUT",assertThrows(WarehouseException.class,()->taxi.book(1,Map.of("company","Yandex Go"),"op")).getMessage(),"phone and car are required");
        taxi.book(1,ride,"op");
        assertEquals("ON_THE_WAY",status(1));
        assertEquals("Ali",taxi.details(1).get("driver_name"));
        taxi.book(1,Map.of("company","MyTaxi","driver_phone","+998900000000","car_number","10 B 1"),"op");
        assertEquals("MyTaxi",taxi.details(1).get("company"),"details can be corrected while on the way");
        taxi.delivered(1,"op");
        assertEquals("DELIVERED",status(1));
        assertNotNull(jdbc.queryForObject("SELECT delivered_at FROM orders WHERE id=1",Object.class));
        assertEquals("TAXI",jdbc.queryForObject("SELECT source FROM order_status_history WHERE order_id=1 AND to_status='DELIVERED'",String.class));
        assertEquals("TAXI_NOT_ON_THE_WAY",assertThrows(WarehouseException.class,()->taxi.delivered(1,"op")).getMessage());
    }
    @Test void failedGoesBackAsReturn(){
        taxi.book(2,ride,"op");
        assertEquals("WAREHOUSE_INVALID_INPUT",assertThrows(WarehouseException.class,()->taxi.failed(2," ","op")).getMessage(),"reason required");
        taxi.failed(2,"Mijoz telefonni ko‘tarmadi","op");
        assertEquals("RETURNING",status(2));
        assertEquals("Mijoz telefonni ko‘tarmadi",taxi.details(2).get("failed_reason"));
    }
    @Test void onlyReadyTaxiOrders(){
        assertEquals("TAXI_NOT_TAXI_ORDER",assertThrows(WarehouseException.class,()->taxi.book(3,ride,"op")).getMessage());
        assertEquals("TAXI_NOT_READY",assertThrows(WarehouseException.class,()->taxi.book(4,ride,"op")).getMessage(),"the warehouse has not packed it yet");
        assertEquals("PREPARING",status(4));
    }
    @Test void availableOnlyInWorkingHours(){
        assertFalse(taxi.available(),"no settings → not offered");
        jdbc.update("INSERT INTO order_timer_settings(id,working_days,working_start,working_end) VALUES(1,127,'00:00','24:00')");
        assertTrue(taxi.available());
        assertEquals("TAXI",TaxiDelivery.method("taxi"));assertEquals("COURIER",TaxiDelivery.method(null));
    }
    @Test void customerConfirmsOrReportsOnlyOwnTaxiOnTheWay() throws Exception {
        var notOnTheWay=assertThrows(Exception.class,()->taxi.customerReceived(1,1));
        assertEquals("This order is not on the way",notOnTheWay.getMessage(),"before the taxi is booked");
        taxi.book(1,ride,"op");
        assertEquals("Order not found",assertThrows(Exception.class,()->taxi.customerReceived(1,2)).getMessage(),"another customer's order");
        assertEquals("Order not found",assertThrows(Exception.class,()->taxi.customerReceived(3,1)).getMessage(),"a courier order");
        taxi.customerProblem(1,1,"Taksi kelmadi");
        assertEquals("ON_THE_WAY",status(1),"a problem report does not change the order");
        assertEquals("Taksi kelmadi",taxi.details(1).get("customer_problem"));
        assertThrows(Exception.class,()->taxi.customerProblem(1,1," "),"reason required");
        taxi.customerReceived(1,1);
        assertEquals("DELIVERED",status(1));
        assertEquals("CUSTOMER",jdbc.queryForObject("SELECT source FROM order_status_history WHERE order_id=1 AND to_status='DELIVERED'",String.class));
        assertEquals(Boolean.TRUE,taxi.details(1).get("confirmed_by_customer"));
    }
    String status(long id){return jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id);}
}
