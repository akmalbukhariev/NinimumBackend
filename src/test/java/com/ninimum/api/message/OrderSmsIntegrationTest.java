package com.ninimum.api.message;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Order SMS on the real schema and status triggers (reports_test); a fake sender, nothing leaves the machine. */
@EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*")
class OrderSmsIntegrationTest {
    JdbcTemplate jdbc;OrderSms sms;List<String> sent;boolean fail;
    @BeforeEach void setup(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("order_sms","taxi_deliveries","order_status_history","orders","users"))jdbc.execute("DELETE FROM "+t);
        jdbc.update("INSERT INTO users(id,region_id,first_name,phone_number,password) VALUES(1,1,'Ali','+998900000001','x'),(2,1,'O‘chirilgan mijoz','deleted-2','')");
        sent=new ArrayList<>();fail=false;
        sms=new OrderSms(jdbc,(phone,text)->{if(fail)throw new IllegalStateException("Eskiz down");sent.add(phone+" | "+text);return true;});
    }
    void order(long id,long user,String status,String method){jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price,delivery_method) VALUES(?,?,?,?,'PAID',5000,5000,?)",id,user,"N"+id,status,method);}

    @Test void offSendsNothing(){
        order(131,1,"ON_THE_WAY","COURIER");
        sms.tick();
        assertTrue(sent.isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_sms",Integer.class));
    }
    @Test void courierTaxiAndDeliveredOnceEach(){
        sms.enable(true,true,true);
        order(131,1,"ON_THE_WAY","COURIER");
        order(132,1,"ON_THE_WAY","TAXI");
        jdbc.update("INSERT INTO taxi_deliveries(order_id,company,driver_phone,car_number,booked_by,booked_at,updated_at) VALUES(132,'Yandex Go','+998901234567','10 A 123 BC','op',NOW(),NOW())");
        order(133,1,"DELIVERED","COURIER");
        order(134,2,"ON_THE_WAY","COURIER");
        sms.scan();sms.scan();
        assertEquals(List.of(
            "+998900000001 | Ninimum: #000131 raqamli buyurtmangiz yo‘lga chiqdi. Kuryer tez orada yetkazib beradi.",
            "+998900000001 | Ninimum: #000132 raqamli buyurtmangiz taksi orqali yo‘lga chiqdi. Haydovchi: +998901234567, mashina: 10 A 123 BC.",
            "+998900000001 | Ninimum: #000133 raqamli buyurtmangiz yetkazildi. Xaridingiz uchun rahmat!"),sent,"once each; a deleted account gets nothing");
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM order_sms WHERE status='SENT'",Integer.class));
    }
    @Test void onlyApprovedTextsAndFailuresAreNotRepeated(){
        sms.enable(false,true,false);
        order(131,1,"ON_THE_WAY","COURIER");
        sms.scan();
        assertTrue(sent.isEmpty(),"courier text not approved yet");
        sms.enable(true,false,false);fail=true;
        sms.scan();sms.scan();
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM order_sms WHERE order_id=131",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_sms",Integer.class),"no second try, no double charge");
    }
}
