package com.ninimum.api.user;

import com.ninimum.api.referral.ReferralService;
import com.ninimum.api.user.service.impl.AccountDeletion;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Account deletion on the real schema (reports_test: audit schema + all migrations from 20261017). */
@EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*")
class AccountDeletionIntegrationTest {
    JdbcTemplate jdbc;AccountDeletion deletion;ReferralService referral;
    @BeforeEach void setup(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("account_deletions","user_delete_feedback","cart_items","user_addresses","referral_rewards","referrals","referral_codes","user_tariffs","order_status_history","orders","users","tariff_plans"))jdbc.execute("DELETE FROM "+t);
        jdbc.update("INSERT INTO tariff_plans(id,name,monthly_price) VALUES(1,'Silver',50000)");
        jdbc.update("UPDATE referral_settings SET promo_mode=1,welcome_for_all=1,friends_needed=5,tariff_plan_id=1 WHERE id=1");
        jdbc.update("INSERT INTO users(id,region_id,first_name,last_name,phone_number,password,address) VALUES(1,1,'Ali','Valiyev','+998900000001','hash','Shahrisabz, 5')");
        jdbc.update("INSERT INTO cart_items(user_id,product_id,quantity) VALUES(1,10,2)");
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,address) VALUES(7,1,'Used by an order'),(8,1,'Never used')");
        jdbc.update("INSERT INTO user_tariffs(user_id,tariff_plan_id,status,start_date,end_date) VALUES(1,1,'ACTIVE',NOW(),DATE_ADD(NOW(),INTERVAL 20 DAY))");
        deletion=new AccountDeletion(jdbc);referral=new ReferralService(jdbc);
    }
    void order(String status){jdbc.update("INSERT INTO orders(user_id,address_id,order_number,status,payment_status,subtotal_price,total_price) VALUES(1,7,?,?,'PAID',5000,5000)","N"+status,status);}

    @Test void waitsForAPaidOrderOnItsWay(){
        order("ON_THE_WAY");
        assertThrows(AccountDeletion.ActiveOrder.class,()->deletion.delete(1,"Narx"));
        assertEquals(1,jdbc.queryForObject("SELECT is_active FROM users WHERE id=1",Integer.class),"nothing changed");
        assertEquals("+998900000001",jdbc.queryForObject("SELECT phone_number FROM users WHERE id=1",String.class));
    }
    @Test void wipesTheAccountKeepsOrdersAndFreesTheNumber() throws Exception{
        order("DELIVERED");
        assertTrue(deletion.delete(1,"Narx qimmat"));
        assertEquals("deleted-1",jdbc.queryForObject("SELECT phone_number FROM users WHERE id=1",String.class));
        assertNull(jdbc.queryForObject("SELECT last_name FROM users WHERE id=1",String.class));
        assertNull(jdbc.queryForObject("SELECT address FROM users WHERE id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT is_active FROM users WHERE id=1",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE user_id=1",Integer.class));
        assertEquals(List.of(7L),jdbc.queryForList("SELECT address_id FROM user_addresses WHERE user_id=1",Long.class),"the order's address stays");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=1",Integer.class),"orders stay");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=1 AND status='ACTIVE'",Integer.class));
        assertEquals("Narx qimmat",jdbc.queryForObject("SELECT reasons FROM user_delete_feedback WHERE user_id=1",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM account_deletions WHERE phone_hash='+998900000001'",Integer.class),"no phone stored");
        assertFalse(deletion.delete(1,null),"already deleted");
        jdbc.update("INSERT INTO users(id,region_id,first_name,phone_number,password) VALUES(2,1,'Ali','+998900000001','hash')");
        referral.registered("+998900000001",null);
        referral.sync(2);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=2",Integer.class),"a returning number gets no second free month");
        jdbc.update("INSERT INTO users(id,region_id,first_name,phone_number,password) VALUES(3,1,'Vali','+998900000003','hash')");
        referral.registered("+998900000003",null);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=3 AND status='ACTIVE'",Integer.class),"a new number still gets it");
    }
}
