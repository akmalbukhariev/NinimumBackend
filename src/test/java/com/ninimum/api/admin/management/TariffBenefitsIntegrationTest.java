package com.ninimum.api.admin.management;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.order.service.OrderMapper;
import com.ninimum.api.order.service.impl.OrderService;
import com.ninimum.api.order.service.impl.PriceChangedException;
import com.ninimum.api.param.*;
import com.ninimum.api.tariff.benefits.TariffBenefitService;
import com.ninimum.api.tariff.service.TariffMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="tariff.test.jdbc",matches=".*")
class TariffBenefitsIntegrationTest {
 JdbcTemplate jdbc;TariffBenefitService benefits;OrderService orders;TransactionTemplate tx;TariffMapper tariffs;com.ninimum.api.cart.service.CartMapper carts;
 @BeforeEach void setup() throws Exception {
  String url=System.getProperty("tariff.test.jdbc");if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/tariff_test"))throw new IllegalArgumentException("Isolated local tariff_test only");
  var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  for(String t:List.of("cart_items","tariff_benefit_history","tariff_product_benefits","user_tariffs","tariff_plans","products"))jdbc.execute("DROP TABLE IF EXISTS "+t);
  jdbc.execute("CREATE TABLE cart_items(id BIGINT PRIMARY KEY,user_id BIGINT,quantity INT,updated_at DATETIME)");
  jdbc.execute("CREATE TABLE tariff_plans(id BIGINT PRIMARY KEY,name VARCHAR(100),monthly_price INT,duration_months INT,description TEXT,is_active BOOLEAN)");
  jdbc.execute("CREATE TABLE products(id BIGINT PRIMARY KEY,name VARCHAR(100),price INT,subscription_price INT,stock_quantity INT,is_active BOOLEAN)");
  jdbc.execute("CREATE TABLE user_tariffs(id BIGINT PRIMARY KEY,tariff_plan_id BIGINT,user_id BIGINT,status VARCHAR(20),start_date DATETIME,end_date DATETIME)");
  var migration=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261017_tariff_benefits.sql"));migration.execute(ds);migration.execute(ds);
  jdbc.update("INSERT INTO tariff_plans(id,name,monthly_price,duration_months,description,is_active) VALUES(1,'Silver',10000,1,'Benefits',1)");
  jdbc.update("INSERT INTO products VALUES(1,'Milk',1001,500,20,1),(2,'Bread',2000,900,20,1)");
  jdbc.update("INSERT INTO user_tariffs VALUES(1,1,7,'ACTIVE',NOW()-INTERVAL 1 DAY,NOW()+INTERVAL 30 DAY)");
  benefits=new TariffBenefitService(jdbc);
  var config=new org.apache.ibatis.session.Configuration();config.getTypeAliasRegistry().registerAlias("camelMap",CamelCaseMap.class);
  var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);factory.setMapperLocations(new FileSystemResource("src/main/resources/mapper/Order/OrderMapper.xml"),new FileSystemResource("src/main/resources/mapper/Tariff/TariffMapper.xml"),new FileSystemResource("src/main/resources/mapper/Cart/CartMapper.xml"));
  var sql=new SqlSessionTemplate(factory.getObject());orders=new OrderService(sql.getMapper(OrderMapper.class));tariffs=sql.getMapper(TariffMapper.class);carts=sql.getMapper(com.ninimum.api.cart.service.CartMapper.class);
 }
 Map<String,Object> rule(int percent){return new HashMap<>(Map.of("use_product_rules",true,"free_delivery",true,"recurring_order_days",7,"products",List.of(Map.of("product_id",1,"discount_percent",percent))));}
 void save(Map<String,Object> body){tx.executeWithoutResult(s->{try{benefits.save(1,body);}catch(Exception ex){throw new RuntimeException(ex);}});}
 CreateOrderParam order(long user){var p=new CreateOrderParam();p.setUserId(user);var a=new CreateOrderProductParam();a.setProductId(1L);a.setQuantity(2);var b=new CreateOrderProductParam();b.setProductId(2L);b.setQuantity(1);p.setProducts(List.of(a,b));return p;}
 @Test void onlyEligibleProductsGetTariffDiscountAndQuoteNeverCreatesOrder() throws Exception {
  save(rule(10));var request=order(7);var quote=orders.quote(request);
  assertEquals(3802,quote.get("total"));assertEquals(901,request.getProducts().get(0).getPrice());assertEquals(2000,request.getProducts().get(1).getPrice());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tariff_benefit_history",Integer.class));
  assertTrue(tariffs.getTariffList().get(0).getFreeDelivery());assertEquals(7,tariffs.getTariffList().get(0).getRecurringOrderDays());assertEquals(1,tariffs.getProductBenefits(1L).size());
  var guest=order(8);assertEquals(4002,orders.quote(guest).get("total"));
 }
 @Test void existingTariffsKeepLegacyPricesAndExpiredMembershipDoesNotDiscount() throws Exception {
  assertEquals(1900,orders.quote(order(7)).get("total"));
  jdbc.update("UPDATE user_tariffs SET end_date=NOW()-INTERVAL 1 HOUR");assertEquals(4002,orders.quote(order(7)).get("total"));
 }
 @Test void invalidChangesRollBackAndClientMustConfirmLatestQuote() throws Exception {
  save(rule(10));assertThrows(RuntimeException.class,()->save(rule(100)));assertEquals(10,jdbc.queryForObject("SELECT discount_percent FROM tariff_product_benefits",Integer.class));
  var request=order(7);request.setQuotedTotal(1);assertThrows(PriceChangedException.class,()->orders.createOrder(request));
  request.setQuotedTotal(null);assertThrows(PriceChangedException.class,()->orders.createOrder(request));
  var forged=order(8);forged.setTariffSubscriptionId(1L);assertThrows(Exception.class,()->orders.quote(forged));
 }
 @Test void cartQuantityAndClearAreScopedToCustomerInRealSql() throws Exception {
  jdbc.update("INSERT INTO cart_items VALUES(1,7,2,NOW()),(2,99,3,NOW())");
  var json=new com.fasterxml.jackson.databind.ObjectMapper();
  var update=json.readValue("{\"cart_id\":2,\"user_id\":7,\"quantity\":9}",UpdateCartParam.class);
  assertEquals(0,carts.updateCart(update));assertEquals(3,jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=2",Integer.class));
  update.setCartId(1L);assertEquals(1,carts.updateCart(update));
  var clear=new ClearCartParam();clear.setUserId(7L);assertEquals(1,carts.clearCart(clear));
  assertEquals(99L,jdbc.queryForObject("SELECT user_id FROM cart_items",Long.class));
 }
}
