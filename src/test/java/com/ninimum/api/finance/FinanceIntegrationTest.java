package com.ninimum.api.finance;

import com.ninimum.api.warehouse.WarehouseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** TZ v10 §9 money section: balances, T18 cash flow, Payme income, supplier payments, Q-18, cash count, monthly profit. Isolated finance_test only. */
@EnabledIfSystemProperty(named="finance.test.jdbc",matches=".*")
class FinanceIntegrationTest {
    static final String PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    JdbcTemplate jdbc;FinanceService finance;long cash,bank,online;
    LocalDate today=FinanceService.today();

    @BeforeEach void setup(){
        String url=System.getProperty("finance.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/finance_test"))throw new IllegalArgumentException("Dedicated local finance_test only");
        var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);
        for(String t:List.of("finance_accounts","expense_types","finance_documents","cash_counts","supplier_payment_records","strict_rules","strict_rules_audit","period_closings","customer_refunds","payments","orders","order_items","products","inventory_documents","delivery_partial_returns","delivery_partial_return_items"))jdbc.execute("DROP TABLE IF EXISTS "+t);
        jdbc.execute("CREATE TABLE payments(id BIGINT PRIMARY KEY,provider VARCHAR(20),amount DECIMAL(16,2),status VARCHAR(20),payme_perform_time BIGINT,payme_cancel_time BIGINT)");
        jdbc.execute("CREATE TABLE supplier_payment_records(id BIGINT AUTO_INCREMENT PRIMARY KEY,supplier_id BIGINT,receipt_id BIGINT,amount DECIMAL(16,2),reference VARCHAR(160),paid_date DATE,status VARCHAR(20) DEFAULT 'RECORDED')");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,status VARCHAR(30),total_price DECIMAL(16,2),delivered_at DATETIME)");
        jdbc.execute("CREATE TABLE order_items(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT,product_id BIGINT,quantity INT,unit_cost_snapshot DECIMAL(16,4))");
        jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,cost_price DECIMAL(16,2))");
        jdbc.execute("CREATE TABLE delivery_partial_returns(order_id BIGINT PRIMARY KEY,refund_amount DECIMAL(16,2))");
        jdbc.execute("CREATE TABLE delivery_partial_return_items(order_id BIGINT,product_id BIGINT,quantity INT)");
        jdbc.execute("CREATE TABLE inventory_documents(id BIGINT AUTO_INCREMENT PRIMARY KEY,kind VARCHAR(30),status VARCHAR(20),approved_at DATETIME,cost_total DECIMAL(16,2))");
        for(String f:List.of("20261021_strict_rules.sql","20261022_strict_rules_value.sql","20261022_finance.sql","20261022_finance.sql","20261022_period_close.sql","20261022_period_close.sql","20261023_customer_refunds.sql"))new ResourceDatabasePopulator(new FileSystemResource("database/migrations/"+f)).execute(ds);
        new com.ninimum.api.admin.access.StrictRules(jdbc);
        new PeriodClose(jdbc);
        finance=new FinanceService(jdbc);
        cash=id("CASH");bank=id("BANK");online=id("ONLINE");
        as("SUPER_ADMIN");
    }
    // Other test classes share the static PeriodClose connection: leave nothing closed.
    @AfterEach void clear(){SecurityContextHolder.clearContext();jdbc.update("DELETE FROM period_closings");}
    long id(String code){return jdbc.queryForObject("SELECT id FROM finance_accounts WHERE code=?",Long.class,code);}
    void as(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("boss",null,List.of(new SimpleGrantedAuthority(role))));}
    BigDecimal bal(long account){return finance.balance(account,today);}
    static BigDecimal n(long v){return BigDecimal.valueOf(v).setScale(2);}
    long doc(String kind,long amount,long account,Map<String,Object> extra){var b=new HashMap<String,Object>(Map.of("kind",kind,"amount",amount,"account_id",account,"doc_date",today.toString()));b.putAll(extra);return finance.create(b);}
    long type(){return jdbc.queryForObject("SELECT id FROM expense_types WHERE name='Ijara'",Long.class);}

    @Test void cashFlowMatchesTzExampleT18(){
        finance.setOpening(cash,Map.of("opening_balance",1000000,"opening_date",today.minusDays(3).toString()));
        doc("INCOME",500000,cash,Map.of("counterparty","Naqd savdo"));
        doc("EXPENSE",200000,cash,Map.of("expense_type_id",type()));
        doc("TRANSFER",300000,cash,Map.of("to_account_id",bank));
        assertEquals(n(1000000),bal(cash));assertEquals(n(300000),bal(bank));
        var row=((List<Map<String,Object>>)finance.movement(today.minusDays(3).toString(),today.toString()).get("accounts")).get(0);
        assertEquals(0,BigDecimal.ZERO.compareTo((BigDecimal)row.get("start")));assertEquals(n(1000000),row.get("opening"));
        var later=((List<Map<String,Object>>)finance.movement(today.toString(),today.toString()).get("accounts")).get(0);
        assertEquals(n(1000000),later.get("start"),"before today: the opening balance");assertEquals(n(1000000),later.get("closing"));
        assertEquals(n(-200000),row.get("expense"));assertEquals(n(-300000),row.get("transfer_out"));assertEquals(n(500000),row.get("income"));assertEquals(n(1000000),row.get("closing"));
    }

    @Test void movementsBeforeTheOpeningDateAreIgnored(){
        jdbc.update("INSERT INTO finance_documents(kind,doc_date,amount,account_id,status,created_by,created_at) VALUES('EXPENSE',?,50,?,'POSTED','x',UTC_TIMESTAMP())",today.minusDays(10),cash);
        assertEquals(n(-50),bal(cash),"without an opening date everything counts");
        finance.setOpening(cash,Map.of("opening_balance",700,"opening_date",today.minusDays(5).toString()));
        assertEquals(n(700),bal(cash));
    }

    @Test void paymeIncomeAndRefundsLandOnTheOnlineAccount(){
        long day=today.atStartOfDay(FinanceService.TASHKENT).toInstant().toEpochMilli()+3600_000;
        jdbc.update("INSERT INTO payments VALUES(1,'PAYME',100000,'PAID',?,0),(2,'PAYME',40000,'CANCELLED',?,?),(3,'PAYME',9000,'CREATED',0,0)",day,day,day+60_000);
        assertEquals(n(100000),bal(online));
        var row=((List<Map<String,Object>>)finance.movement(today.toString(),today.toString()).get("accounts")).stream().filter(r->((Number)r.get("id")).longValue()==online).findFirst().orElseThrow();
        assertEquals(n(140000),row.get("payme"));assertEquals(n(-40000),row.get("payme_refund"));
    }

    @Test void supplierPaymentsCountOnlyWithAPlace(){
        jdbc.update("INSERT INTO supplier_payment_records(supplier_id,receipt_id,amount,reference,paid_date,account_id) VALUES(1,1,50000,'A',?,?),(1,1,7000,'B',?,NULL)",today,cash,today);
        jdbc.update("INSERT INTO supplier_payment_records(supplier_id,receipt_id,amount,reference,paid_date,account_id,status) VALUES(1,1,999,'C',?,?,'VOID')",today,cash);
        assertEquals(n(-50000),bal(cash));
    }

    @Test void staffNeedAReceiptPhotoForAnExpenseQ18AndReversalRemovesIt(){
        as("ADMIN");
        assertEquals("FINANCE_PHOTO_REQUIRED",assertThrows(WarehouseException.class,()->doc("EXPENSE",1000,cash,Map.of("expense_type_id",type()))).getMessage());
        long id=doc("EXPENSE",1000,cash,Map.of("expense_type_id",type(),"receipt_photo_base64",PNG,"counterparty","Landlord"));
        assertEquals(n(-1000),bal(cash));
        assertTrue(String.valueOf(finance.photo(id).get("photo_data")).startsWith("data:image/"));
        finance.reverse(id,"Wrong amount");
        assertEquals(n(0),bal(cash));
        assertEquals("WAREHOUSE_INVALID_STATE",assertThrows(WarehouseException.class,()->finance.reverse(id,"again")).getMessage());
        assertEquals("FINANCE_FUTURE_DATE",assertThrows(WarehouseException.class,()->finance.create(Map.of("kind","INCOME","amount",1,"account_id",cash,"doc_date",today.plusDays(1).toString()))).getMessage());
        assertEquals("FINANCE_INVALID_AMOUNT",assertThrows(WarehouseException.class,()->doc("INCOME",0,cash,Map.of())).getMessage());
        assertEquals("WAREHOUSE_INVALID_INPUT",assertThrows(WarehouseException.class,()->doc("TRANSFER",5,cash,Map.of("to_account_id",cash))).getMessage());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->finance.saveExpenseType(null,Map.of("name","Soliq")),"only the owner edits expense types");
    }

    @Test void cashCountShowsTheDifference(){
        finance.setOpening(cash,Map.of("opening_balance",1000000,"opening_date",today.toString()));
        var r=finance.count(Map.of("account_id",cash,"counted_amount",999000));
        assertEquals(n(-1000),r.get("difference"));assertEquals(1,finance.counts().size());
    }

    @Test void monthlyResultIsSalesMinusCostMinusExpenses(){
        String now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1).toString().replace('T',' ').substring(0,19);
        jdbc.update("INSERT INTO orders VALUES(1,'DELIVERED',10000,?),(2,'CANCELLED',5000,?),(3,'DELIVERED',777,'2020-01-01 00:00:00')",now,now);
        jdbc.update("INSERT INTO delivery_partial_returns VALUES(1,1000)");
        jdbc.update("INSERT INTO delivery_partial_return_items VALUES(1,1,1)");
        jdbc.update("INSERT INTO products VALUES(1,9999),(2,500),(3,NULL)");
        jdbc.update("INSERT INTO order_items(order_id,product_id,quantity,unit_cost_snapshot) VALUES(1,1,2,2000),(1,2,1,NULL),(1,3,1,NULL),(2,1,5,1)");
        doc("EXPENSE",3000,cash,Map.of("expense_type_id",type()));
        var m=finance.monthly(null);
        assertEquals(n(9000),m.get("sales"));assertEquals(0,new BigDecimal("2500").compareTo((BigDecimal)m.get("cost")),"one returned piece of product 1 is not a cost");
        assertEquals(1L,m.get("cost_estimated_lines"));assertEquals(1L,m.get("cost_unknown_lines"));
        assertEquals(n(3000),m.get("expenses"));assertEquals(0,new BigDecimal("3500").compareTo((BigDecimal)m.get("profit")));
        assertEquals(new BigDecimal("38.9"),m.get("margin_percent"));
    }

    // TZ v12 B13: write-offs and count shortages lower the profit; a surplus and other months do not change it.
    @Test void monthlyResultSubtractsWriteOffsAndShortages(){
        String now=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1).toString().replace('T',' ').substring(0,19);
        jdbc.update("INSERT INTO orders VALUES(1,'DELIVERED',10000,?)",now);
        jdbc.update("INSERT INTO inventory_documents(kind,status,approved_at,cost_total) VALUES('WRITEOFF','POSTED',?,5000),('COUNT','POSTED',?,-450),('COUNT','POSTED',?,300),('WRITEOFF','DRAFT',?,9),('WRITEOFF','POSTED','2020-01-01 00:00:00',7),('OPENING','POSTED',?,1000)",now,now,now,now,now);
        var m=finance.monthly(null);
        assertEquals(0,new BigDecimal("5450").compareTo((BigDecimal)m.get("losses")));
        assertEquals(0,new BigDecimal("5000").compareTo((BigDecimal)m.get("losses_writeoff")));
        assertEquals(0,new BigDecimal("450").compareTo((BigDecimal)m.get("losses_shortage")));
        assertEquals(0,new BigDecimal("4550").compareTo((BigDecimal)m.get("profit")));
    }

    // TZ v10 T20: the period is closed until a date; a document dated on or before it is rejected (Q-16, staff and owner by default).
    @Test void closedPeriodRejectsDocumentsT20(){
        var period=new PeriodClose(jdbc);
        assertEquals("PERIOD_FUTURE_DATE",assertThrows(WarehouseException.class,()->period.set(Map.of("closed_until",today.plusDays(1).toString()))).getMessage());
        jdbc.update("INSERT INTO finance_documents(kind,doc_date,amount,account_id,status,created_by,created_at) VALUES('INCOME',?,50,?,'POSTED','x',UTC_TIMESTAMP())",today.minusDays(5),cash);
        long old=jdbc.queryForObject("SELECT MAX(id) FROM finance_documents",Long.class);
        period.set(Map.of("closed_until",today.minusDays(2).toString()));
        assertEquals(today.minusDays(2).toString(),period.state().get("closed_until"));
        as("ADMIN");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->period.set(Map.of("closed_until","")),"only the owner");
        assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,()->doc("INCOME",10,cash,Map.of("doc_date",today.minusDays(2).toString()))).getMessage());
        doc("INCOME",10,cash,Map.of("doc_date",today.minusDays(1).toString()));
        as("SUPER_ADMIN");
        assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,()->finance.reverse(old,"late fix")).getMessage(),"owner too, while the owner switch is on");
        assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,()->finance.setOpening(cash,Map.of("opening_balance",5,"opening_date",today.minusDays(9).toString()))).getMessage());
        assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,()->finance.count(Map.of("account_id",cash,"counted_amount",1,"count_date",today.minusDays(3).toString()))).getMessage());
        PeriodClose.guardToday();
        assertEquals("PERIOD_REASON_REQUIRED",assertThrows(WarehouseException.class,()->period.set(Map.of("closed_until",today.minusDays(6).toString()))).getMessage(),"reopening needs a reason");
        period.set(Map.of("closed_until",today.toString()));
        assertEquals("PERIOD_CLOSED",assertThrows(WarehouseException.class,PeriodClose::guardToday).getMessage(),"receipts and inventory are dated today");
        jdbc.update("UPDATE strict_rules SET owner_enabled=0 WHERE code='Q-16'");
        finance.reverse(old,"owner switch off");
        jdbc.update("UPDATE strict_rules SET owner_enabled=1 WHERE code='Q-16'");
        period.set(Map.of("closed_until","","reason","Correction"));
        assertNull(period.state().get("closed_until"));
        assertEquals(3,((List<?>)period.state().get("history")).size());
        doc("INCOME",10,cash,Map.of("doc_date",today.minusDays(5).toString()));
    }
}
