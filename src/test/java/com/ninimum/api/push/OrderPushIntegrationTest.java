package com.ninimum.api.push;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Order push on the real schema and status triggers (reports_test); a fake sender records the messages. */
@EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*")
class OrderPushIntegrationTest {
    JdbcTemplate jdbc;OrderPush orders;List<String> sent;boolean on;
    @BeforeEach void setup(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("push_log","push_tokens","order_status_history","orders"))jdbc.execute("DELETE FROM "+t);
        sent=new ArrayList<>();on=true;
        orders=new OrderPush(jdbc,new Push(){
            public boolean enabled(){return on;}
            public void send(String app,String owner,String title,String body,Map<String,String> data){sent.add(app+"|"+owner+"|"+title+"|"+body);}
        });
    }
    void order(long id,String status,String method){jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price,delivery_method) VALUES(?,7,?,?,'PAID',5000,5000,?)",id,"N"+id,status,method);}

    @Test void eachAppGetsItsMessageOnce(){
        order(131,"CONFIRMED","COURIER");
        order(132,"READY","COURIER");
        order(133,"READY","TAXI");
        orders.tick();orders.tick();
        assertEquals(List.of(
            "CUSTOMER|7|To‘lov qabul qilindi · #000131|To‘lov qabul qilindi. Buyurtmangizni tayyorlaymiz.",
            "WAREHOUSE|null|Yangi buyurtma #000131|To‘langan buyurtma navbatda. Tayyorlashni boshlang.",
            "CUSTOMER|7|Buyurtma tayyor · #000132|Buyurtmangiz tayyor. Kuryer tez orada olib ketadi.",
            "COURIER|null|Yetkazishga tayyor #000132|Buyurtma qadoqlandi va olib ketishni kutmoqda.",
            "CUSTOMER|7|Buyurtma tayyor · #000133|Buyurtmangiz tayyor. Taksi chaqirilmoqda."),sent,"taxi orders aren't offered to couriers");
    }
    @Test void offSendsNothingAndPendingIsQuiet(){
        on=false;
        order(131,"CONFIRMED","COURIER");
        orders.tick();
        assertTrue(sent.isEmpty());
        on=true;
        order(134,"PENDING","COURIER");
        orders.tick();
        assertEquals(2,sent.stream().filter(s->s.contains("#000131")).count(),"customer + warehouse, once, after switching on");
        assertTrue(sent.stream().noneMatch(s->s.contains("#000134")),"an unpaid order is not announced");
    }
}
