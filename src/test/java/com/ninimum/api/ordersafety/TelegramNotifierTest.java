package com.ninimum.api.ordersafety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** K7 Telegram alerts: off without settings, and the message names the problem, the order and the admin link. */
class TelegramNotifierTest {
    @Test void offUnlessTokenAndChatAreSet(){
        assertFalse(new TelegramNotifier(null,"","","").enabled());
        assertFalse(new TelegramNotifier(null,"t"," , ","").enabled());
        assertTrue(new TelegramNotifier(null,"t","1, 2","").enabled());
        assertFalse(new TelegramNotifier(null,"","1","").test(),"nothing is sent while off");
    }
    @Test @EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*") void orderMessage(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        var jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");jdbc.execute("DELETE FROM order_status_history");jdbc.execute("DELETE FROM orders WHERE id=95");
        jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price) VALUES(95,1,'N95','READY','PAID',1,1)");
        String text=new TelegramNotifier(jdbc,"t","1","http://admin/").message("ORDER",95,"COURIER_WAIT",35);
        assertTrue(text.contains("Kuryer biriktirilmagan"));assertTrue(text.contains("Buyurtma #95 (N95)"));assertTrue(text.contains("35 daqiqa"));
        assertTrue(text.endsWith("http://admin/orders?order=95"),text);
        assertTrue(new TelegramNotifier(jdbc,"t","1","").message("ORDER",95,"DELIVERY_DELAY",180).contains("3 soat"));
    }
}
