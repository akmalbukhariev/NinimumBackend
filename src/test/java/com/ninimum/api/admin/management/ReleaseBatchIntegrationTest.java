package com.ninimum.api.admin.management;
import com.ninimum.api.admin.management.service.*;
import com.ninimum.api.admin.management.service.impl.AdminManagementService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.product.service.ProductMapper;
import com.ninimum.api.productquestion.service.*;
import com.ninimum.api.productquestion.service.impl.ProductQuestionService;
import com.ninimum.api.product.validation.ProductMetadata;
import com.ninimum.api.param.*;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.warehouse.*;
import com.ninimum.api.deliveryapp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.mybatis.spring.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="batch.test.jdbc",matches=".*")
class ReleaseBatchIntegrationTest {
 JdbcTemplate jdbc;ProductMapper products;ProductQuestionService questions;AdminManagementService admin;
 @BeforeEach void setup() throws Exception {
  String url=System.getProperty("batch.test.jdbc");if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/batch_test"))throw new IllegalArgumentException("Dedicated local batch_test only");
  var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);
  for(String table:List.of("product_price_history","product_questions","products","users","order_timer_settings"))jdbc.execute("DROP TABLE IF EXISTS "+table);
  jdbc.execute("CREATE TABLE products(id BIGINT AUTO_INCREMENT PRIMARY KEY,category_id BIGINT,fiscal_mxik_package_id BIGINT,vat_percent INT,name VARCHAR(255),short_description TEXT,description TEXT,sku VARCHAR(100),barcode VARCHAR(100),brand VARCHAR(100),price DECIMAL(14,2),subscription_price DECIMAL(14,2),stock_quantity INT,weight_gram DOUBLE,is_featured BOOLEAN,updated_at DATETIME)");
  jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,first_name VARCHAR(80),last_name VARCHAR(80),phone_number VARCHAR(40))");
  jdbc.execute("CREATE TABLE product_questions(id BIGINT AUTO_INCREMENT PRIMARY KEY,product_id BIGINT,user_id BIGINT,question TEXT,answer TEXT,status VARCHAR(30),is_active BOOLEAN,created_at DATETIME,answered_at DATETIME,updated_at DATETIME)");
  jdbc.execute("CREATE TABLE order_timer_settings(id INT PRIMARY KEY,question_minutes INT)");jdbc.update("INSERT INTO order_timer_settings VALUES(1,120)");
  for(String file:List.of("20261017_product_metadata.sql","20261017_complaints.sql","20261017_product_price_history.sql")) {var migration=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/"+file));migration.execute(ds);migration.execute(ds);}
  var config=new org.apache.ibatis.session.Configuration();config.getTypeAliasRegistry().registerAlias("camelMap",CamelCaseMap.class);
  var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
  factory.setMapperLocations(new FileSystemResource("src/main/resources/mapper/Product/ProductMapper.xml"),new FileSystemResource("src/main/resources/mapper/ProductQuestion/ProductQuestionMapper.xml"),new FileSystemResource("src/main/resources/mapper/AdminManagement/AdminManagementMapper.xml"));
  var sql=new SqlSessionTemplate(factory.getObject());products=sql.getMapper(ProductMapper.class);questions=new ProductQuestionService(sql.getMapper(ProductQuestionMapper.class));
  admin=new AdminManagementService(sql.getMapper(AdminManagementMapper.class),mock(FileService.class),mock(WarehouseAppService.class),mock(WarehouseService.class),mock(ReturnPhotoService.class),mock(PartialReturnService.class));
  jdbc.update("INSERT INTO users VALUES(1,'First','Customer','001'),(2,'Second','Customer','002')");
 }
 @Test void productMetadataPersistsOnCreateUpdateAndClearWithoutOverwritingOtherFields() throws Exception {
  var p=new AddProductParam();p.setName("Milk");p.setPrice(100.0);p.setStock_quantity(2);p.setVat_percent(12);p.setCost_price(new BigDecimal("80"));p.setIngredients(" Milk ");p.setShelf_life_days(180);ProductMetadata.normalize(p);products.insertProduct(p);
  long id=jdbc.queryForObject("SELECT id FROM products WHERE name='Milk'",Long.class);assertEquals("Milk",jdbc.queryForObject("SELECT ingredients FROM products WHERE id=?",String.class,id));
  admin.updateProduct(id,new HashMap<>(Map.of("cost_price","90.50","min_stock_quantity",5,"storage_temperature"," 2–8 C ")));
  assertEquals(new BigDecimal("90.50"),jdbc.queryForObject("SELECT cost_price FROM products WHERE id=?",BigDecimal.class,id));
  assertEquals(180,jdbc.queryForObject("SELECT shelf_life_days FROM products WHERE id=?",Integer.class,id));
  var clear=new HashMap<String,Object>();clear.put("cost_price",null);clear.put("ingredients","");admin.updateProduct(id,clear);
  assertNull(jdbc.queryForMap("SELECT cost_price FROM products WHERE id=?",id).get("cost_price"));assertNull(jdbc.queryForMap("SELECT ingredients FROM products WHERE id=?",id).get("ingredients"));
  assertEquals(2,admin.getProductPriceHistory(id).size());
  assertThrows(IllegalArgumentException.class,()->admin.updateProduct(id,new HashMap<>(Map.of("cost_price","-1"))));assertEquals(2,jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,id));
 }
 @Test void privateComplaintsAreVisibleOnlyToTheirAuthorAndAdminAndCanBeAnswered() throws Exception {
  jdbc.update("INSERT INTO products(id,name) VALUES(1,'Milk')");
  for(var value:List.of(Map.of("user",1L,"kind","QUESTION"),Map.of("user",1L,"kind","COMPLAINT"),Map.of("user",2L,"kind","COMPLAINT"))) {var p=new AddProductQuestionParam();p.setUser_id((Long)value.get("user"));p.setProduct_id(1L);p.setKind(value.get("kind").toString());p.setQuestion("Product issue");questions.addQuestion(p);}
  var query=new ProductQuestionListParam();query.setProduct_id(1L);query.setPageSize(20);assertEquals(1,questions.getQuestionList(query).size());
  query.setViewer_id(1L);var own=questions.getQuestionList(query);assertEquals(2,own.size());assertTrue(own.stream().allMatch(q->q.getUser_id()==1L));
  var complaintPage=admin.getFilteredQuestions(null,null,"COMPLAINT",1,20);assertEquals(2,complaintPage.get("total"));
  var complaint=own.stream().filter(q->"COMPLAINT".equals(q.getKind())).findFirst().orElseThrow();admin.answerQuestion(complaint.getId(),"We will replace it.");
  assertEquals("ANSWERED",questions.getQuestionList(query).stream().filter(q->q.getId().equals(complaint.getId())).findFirst().orElseThrow().getStatus());
  query.setViewer_id(null);assertEquals(1,questions.getQuestionList(query).size());
 }
 @Test void priceEditsRequireStaffReasonAndAuditOnlyActualChanges() {
  jdbc.update("INSERT INTO products(id,name,price,stock_quantity) VALUES(1,'Milk',100,2)");
  var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
  context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("operator",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN"))));
  try {
   assertThrows(IllegalArgumentException.class,()->admin.updateProduct(1,new HashMap<>(Map.of("price","120"))));
   assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("SELECT price FROM products WHERE id=1",BigDecimal.class));
   admin.updateProduct(1,new HashMap<>(Map.of("price","120.50","price_change_reason","Supplier cost changed")));
   admin.updateProduct(1,new HashMap<>(Map.of("price","120.5")));
   var history=admin.getProductPriceHistory(1);assertEquals(1,history.size());assertEquals("operator",history.get(0).get("changed_by"));assertEquals(new BigDecimal("100.00"),history.get(0).get("old_amount"));assertEquals("Supplier cost changed",history.get(0).get("reason"));
   jdbc.execute("CREATE TRIGGER fail_price_history BEFORE INSERT ON product_price_history FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
   var tx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
   assertThrows(RuntimeException.class,()->tx.executeWithoutResult(status->admin.updateProduct(1,new HashMap<>(Map.of("price","130","price_change_reason","Test rollback")))));
   assertEquals(new BigDecimal("120.50"),jdbc.queryForObject("SELECT price FROM products WHERE id=1",BigDecimal.class));
  }finally{context.setAuthentication(null);}
 }
}
