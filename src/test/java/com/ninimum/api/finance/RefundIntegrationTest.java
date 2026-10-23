package com.ninimum.api.finance;

import com.ninimum.api.warehouse.WarehouseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** K5 "Qaytarish kerak" on the real production schema (reports_test: audit schema + all migrations from 20261017). */
@EnabledIfSystemProperty(named="reports.test.jdbc",matches=".*")
class RefundIntegrationTest {
    JdbcTemplate jdbc;RefundService refunds;FinanceService finance;long cash;

    @BeforeEach void setup(){
        String url=System.getProperty("reports.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test"))throw new IllegalArgumentException("Dedicated local reports_test only");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(url,"root","",true));
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String t:List.of("customer_refunds","period_closings","order_status_history","delivery_partial_returns","order_items","orders","payments","users"))jdbc.execute("DELETE FROM "+t);
        new PeriodClose(jdbc);refunds=new RefundService(jdbc);finance=new FinanceService(jdbc);
        cash=jdbc.queryForObject("SELECT id FROM finance_accounts WHERE code='CASH'",Long.class);
        jdbc.update("INSERT INTO users(id,region_id,first_name,phone_number,password) VALUES(1,1,'Ali','+998901112233','x')");
        // #1 paid and cancelled → whole payment back in Payme; #2 delivered with a partial return; #3 cancelled and already refunded by Payme.
        jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price,ordered_at,cancel_reason) VALUES(1,1,'N1','CANCELLED','PAID',5000,5000,UTC_TIMESTAMP(),'Mijoz voz kechdi'),(2,1,'N2','DELIVERED','PAID',9000,9000,UTC_TIMESTAMP(),NULL),(3,1,'N3','CANCELLED','REFUNDED',700,700,UTC_TIMESTAMP(),NULL)");
        long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO payments(order_id,user_id,provider,provider_transaction_id,amount,amount_tiyin,status,payme_perform_time,payme_cancel_time) VALUES(1,1,'PAYME','tx-1',5000,500000,'PAID',?,0),(2,1,'PAYME','tx-2',9000,900000,'PAID',?,0),(3,1,'PAYME','tx-3',700,70000,'CANCELLED',?,?)",now,now,now,now+1000);
        jdbc.update("INSERT INTO delivery_partial_returns(order_id,delivery_job_id,status,reason,actor,refund_amount,refund_status,created_at) VALUES(2,1,'RECEIVED','Singan','c',1000,'PENDING',UTC_TIMESTAMP())");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("boss",null,List.of(new SimpleGrantedAuthority("SUPER_ADMIN"))));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();jdbc.update("DELETE FROM period_closings");}
    List<Map<String,Object>> pending(){return (List<Map<String,Object>>)refunds.list().get("pending");}
    List<Map<String,Object>> done(){return (List<Map<String,Object>>)refunds.list().get("done");}

    @Test void listShowsEveryoneStillOwedMoney(){
        var p=pending();assertEquals(2,p.size());
        var full=p.stream().filter(r->"FULL".equals(r.get("kind"))).findFirst().orElseThrow();
        assertEquals("N1",full.get("order_number"));assertEquals("tx-1",full.get("payme_id"));assertEquals(0,new BigDecimal("5000").compareTo((BigDecimal)full.get("amount")));assertEquals("Ali",full.get("customer_name"));
        var part=p.stream().filter(r->"PARTIAL".equals(r.get("kind"))).findFirst().orElseThrow();
        assertEquals("N2",part.get("order_number"));assertEquals(0,new BigDecimal("1000").compareTo((BigDecimal)part.get("amount")));
        assertTrue(done().stream().anyMatch(r->"N3".equals(r.get("order_number"))),"Payme refund of #3 is in the finished list");
    }
    @Test void partialRefundIsRecordedOnceAndLeavesCash(){
        assertEquals("WAREHOUSE_INVALID_INPUT",assertThrows(WarehouseException.class,()->refunds.recordPartial(2,Map.of("account_id",cash))).getMessage(),"note is required");
        assertEquals("REFUND_NOT_PENDING",assertThrows(WarehouseException.class,()->refunds.recordPartial(1,Map.of("account_id",cash,"note","x"))).getMessage(),"a full refund goes through Payme");
        refunds.recordPartial(2,Map.of("account_id",cash,"note","Naqd berildi"));
        assertEquals(1,pending().size());
        assertEquals("MANUAL_REFUND",jdbc.queryForObject("SELECT refund_status FROM delivery_partial_returns WHERE order_id=2",String.class));
        assertTrue(done().stream().anyMatch(r->"N2".equals(r.get("order_number"))&&"Naqd berildi".equals(r.get("note"))));
        assertEquals(0,new BigDecimal("-1000").compareTo(finance.balance(cash,FinanceService.today())),"cash paid the refund");
        assertEquals("REFUND_NOT_PENDING",assertThrows(WarehouseException.class,()->refunds.recordPartial(2,Map.of("account_id",cash,"note","again"))).getMessage());
    }
    @Test void closedPeriodBlocksABackdatedRefund(){
        jdbc.update("INSERT INTO period_closings(closed_until,changed_by,changed_at) VALUES(?,'t',UTC_TIMESTAMP())",FinanceService.today().minusDays(1));
        assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,()->refunds.recordPartial(2,Map.of("account_id",cash,"note","x","refund_date",FinanceService.today().minusDays(1).toString()))).getMessage());
        refunds.recordPartial(2,Map.of("account_id",cash,"note","x"));
    }
}
