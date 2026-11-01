package com.ninimum.api.referral;

import com.ninimum.api.warehouse.WarehouseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Promo period on the real schema (reports_test: audit schema + all migrations from 20261017). */
@EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*")
class ReferralIntegrationTest {
    JdbcTemplate jdbc;ReferralService referral;long plan;
    @BeforeEach void setup(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("referrals","referral_rewards","referral_codes","user_tariffs","users","tariff_plans"))jdbc.execute("DELETE FROM "+t);
        jdbc.update("INSERT INTO tariff_plans(id,name,monthly_price) VALUES(1,'Silver',50000)");plan=1;
        jdbc.update("UPDATE referral_settings SET promo_mode=0,welcome_for_all=0,friends_needed=2,tariff_plan_id=NULL WHERE id=1");
        user(1,"+998900000001");
        referral=new ReferralService(jdbc);
    }
    void user(long id,String phone){jdbc.update("INSERT INTO users(id,region_id,first_name,phone_number,password) VALUES(?,1,'Test',?,'x')",id,phone);}
    int tariffs(long user){return jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=? AND status='ACTIVE'",Integer.class,user);}
    void promo(){referral.save(Map.of("promo_mode",true,"friends_needed",2,"tariff_plan_id",plan));}

    @Test void offByDefaultAndNeedsAPlan(){
        assertFalse(referral.promoMode());
        assertEquals("REFERRAL_PLAN_REQUIRED",assertThrows(WarehouseException.class,()->referral.save(Map.of("promo_mode",true,"friends_needed",5))).getMessage());
        assertEquals("WAREHOUSE_INVALID_INPUT",assertThrows(WarehouseException.class,()->referral.save(Map.of("promo_mode",false,"friends_needed",0))).getMessage());
        String code=String.valueOf(referral.me(1).get("code"));
        user(2,"+998900000002");
        referral.registered("+998900000002",code);
        assertEquals(0,tariffs(2),"a code does nothing while the promo is off");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM referrals",Integer.class));
    }
    @Test void friendGetsAMonthAndInviterAfterEnoughFriends(){
        promo();
        String code=String.valueOf(referral.me(1).get("code"));
        assertEquals(code,referral.me(1).get("code"),"the code stays the same");
        assertFalse(referral.validCode("NOPE123"));
        assertTrue(referral.validCode(" "+code.toLowerCase()+" "),"spaces and small letters are fine");
        user(2,"+998900000002");
        referral.registered("+998900000002",code);
        assertEquals(1,tariffs(2),"the new friend gets the month");
        assertTrue(jdbc.queryForObject("SELECT DATEDIFF(end_date,start_date) FROM user_tariffs WHERE user_id=2",Integer.class)>=28,"one month");
        assertEquals(0,tariffs(1),"one friend is not enough yet");
        assertEquals(1,referral.me(1).get("friends_count"));
        user(3,"+998900000003");
        referral.registered("+998900000003",code);
        assertEquals(1,tariffs(1),"second friend: the inviter gets the month");
        assertEquals(true,referral.me(1).get("rewarded"));
        user(4,"+998900000004");
        referral.registered("+998900000004",code);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=1",Integer.class),"only one month");
        assertEquals("Silver",jdbc.queryForObject("SELECT p.name FROM user_tariffs u JOIN tariff_plans p ON p.id=u.tariff_plan_id WHERE u.user_id=1",String.class));
    }
    @Test void existingTariffGetsLongerAndLoweredCountApplies(){
        promo();
        String code=String.valueOf(referral.me(1).get("code"));
        user(2,"+998900000002");
        referral.registered("+998900000002",code);
        jdbc.update("INSERT INTO user_tariffs(user_id,tariff_plan_id,status,start_date,end_date) VALUES(1,1,'ACTIVE',NOW(),DATE_ADD(NOW(),INTERVAL 5 DAY))");
        referral.save(Map.of("promo_mode",true,"friends_needed",1,"tariff_plan_id",plan));
        assertEquals(true,referral.me(1).get("rewarded"));
        assertEquals(1,tariffs(1));
        assertTrue(jdbc.queryForObject("SELECT DATEDIFF(end_date,NOW()) FROM user_tariffs WHERE user_id=1",Integer.class)>=30,"a month longer, no second tariff");
    }
    @Test void welcomeForAllGivesTheMonthWithoutACode(){
        promo();
        user(2,"+998900000002");
        referral.registered("+998900000002",null);
        assertEquals(0,tariffs(2),"switch off: no code, no month");
        referral.save(Map.of("promo_mode",true,"welcome_for_all",true,"friends_needed",1,"tariff_plan_id",plan));
        assertEquals(true,referral.admin().get("welcome_for_all"));
        user(3,"+998900000003");
        referral.registered("+998900000003","");
        assertEquals(1,tariffs(3),"switch on: the month without a code");
        assertEquals(0,tariffs(1),"nobody invited this customer");
        String code=String.valueOf(referral.me(1).get("code"));
        user(4,"+998900000004");
        referral.registered("+998900000004",code);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=4",Integer.class),"a code doesn't add a second month");
        assertEquals(1,tariffs(1),"the inviter still gets the credit");
        referral.save(Map.of("promo_mode",false,"welcome_for_all",true,"friends_needed",1,"tariff_plan_id",plan));
        user(5,"+998900000005");
        referral.registered("+998900000005",null);
        assertEquals(0,tariffs(5),"promo off: nothing is given");
    }
    @Test void switchAppliesToRegisteredAccounts(){
        promo();
        String code=String.valueOf(referral.me(1).get("code"));
        user(2,"+998900000002");user(3,"+998900000003");user(4,"+998900000004");
        referral.registered("+998900000002",null);
        referral.registered("+998900000003",code);
        jdbc.update("INSERT INTO user_tariffs(id,user_id,tariff_plan_id,status,start_date,end_date) VALUES(900,4,1,'ACTIVE',NOW(),DATE_ADD(NOW(),INTERVAL 5 DAY))");
        Map<String,Object> on=Map.of("promo_mode",true,"welcome_for_all",true,"friends_needed",5,"tariff_plan_id",plan),off=Map.of("promo_mode",true,"welcome_for_all",false,"friends_needed",5,"tariff_plan_id",plan);
        referral.sync(2);
        assertEquals(0,tariffs(2),"switch off: nothing changes");
        referral.save(on);
        referral.sync(2);referral.sync(2);referral.sync(4);
        assertEquals(1,tariffs(2),"switch on: the registered account gets the month, once");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=4",Integer.class),"an active tariff stays alone");
        referral.save(off);
        referral.sync(2);referral.sync(3);referral.sync(4);
        assertEquals(0,tariffs(2),"switch off: the month goes back");
        assertEquals(1,tariffs(3),"a month from a code stays");
        assertEquals(1,tariffs(4),"another tariff stays");
        referral.save(on);
        referral.sync(2);
        assertEquals(1,tariffs(2),"switch on again: the month returns");
        jdbc.update("UPDATE user_tariffs SET end_date=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE user_id=2 AND status='ACTIVE'");
        referral.sync(2);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM referral_rewards WHERE user_id=2",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_tariffs WHERE user_id=2 AND status='ACTIVE' AND end_date>=NOW()",Integer.class),"a finished month isn't given again");
    }
    @Test void bannedInviterCodeIsRefused(){
        promo();
        String code=String.valueOf(referral.me(1).get("code"));
        jdbc.update("UPDATE users SET status='BANNED' WHERE id=1");
        assertFalse(referral.validCode(code));
    }
}
