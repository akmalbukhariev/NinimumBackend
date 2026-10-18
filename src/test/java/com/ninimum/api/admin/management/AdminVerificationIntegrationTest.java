package com.ninimum.api.admin.management;

import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.admin.management.service.impl.AdminManagementService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.warehouse.*;
import com.ninimum.api.deliveryapp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Destructive fixtures restricted to a dedicated, isolated loopback database. */
@EnabledIfSystemProperty(named="verification.test.jdbc",matches=".*")
class AdminVerificationIntegrationTest {
    AdminManagementMapper mapper;
    com.ninimum.api.product.service.ProductMapper catalog;
    AdminManagementService service;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    @BeforeEach void setup() throws Exception {
        String url=System.getProperty("verification.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/verification_test"))
            throw new IllegalArgumentException("Dedicated local verification_test only");
        var ds=new DriverManagerDataSource(url,"root","");
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        for(String table:List.of("delivery_partial_returns","order_status_history","order_items","categories","orders","users","app_configs")) jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("CREATE TABLE categories(id BIGINT AUTO_INCREMENT PRIMARY KEY,parent_id BIGINT NULL,name VARCHAR(255),image_url TEXT,sort_order INT,is_active BOOLEAN,created_at DATETIME,updated_at DATETIME)");
        var localization=new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261016_category_localization.sql"));
        localization.execute(ds);localization.execute(ds);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,first_name VARCHAR(50),last_name VARCHAR(50),phone_number VARCHAR(30))");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,user_id BIGINT,order_number VARCHAR(100),status VARCHAR(30))");
        jdbc.update("INSERT INTO users VALUES(1,'Test','Customer','998901234587')");
        jdbc.update("INSERT INTO orders VALUES(87,1,'ORD-EXACT-87','PENDING'),(86,1,'ORD-OTHER','DELIVERED')");
        jdbc.execute("ALTER TABLE orders ADD COLUMN payment_status VARCHAR(30), ADD COLUMN ordered_at DATETIME(6), ADD COLUMN cancelled_at DATETIME, ADD COLUMN subtotal_price DECIMAL(16,2), ADD COLUMN delivery_price DECIMAL(16,2), ADD COLUMN discount_price DECIMAL(16,2), ADD COLUMN total_price DECIMAL(16,2)");
        jdbc.execute("CREATE TABLE order_items(id BIGINT PRIMARY KEY,order_id BIGINT,quantity INT)");
        jdbc.execute("CREATE TABLE order_status_history(id BIGINT PRIMARY KEY,order_id BIGINT,to_status VARCHAR(30),changed_at DATETIME)");
        jdbc.execute("CREATE TABLE delivery_partial_returns(order_id BIGINT PRIMARY KEY,status VARCHAR(30),refund_status VARCHAR(30),refund_amount DECIMAL(16,2),created_at DATETIME)");
        var config=new org.apache.ibatis.session.Configuration();
        config.getTypeAliasRegistry().registerAlias("camelMap",CamelCaseMap.class);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        factory.setMapperLocations(new FileSystemResource("src/main/resources/mapper/AdminManagement/AdminManagementMapper.xml"),new FileSystemResource("src/main/resources/mapper/Product/ProductMapper.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(AdminManagementMapper.class);
        catalog=new SqlSessionTemplate(factory.getObject()).getMapper(com.ninimum.api.product.service.ProductMapper.class);
        service=new AdminManagementService(mapper,mock(FileService.class),mock(WarehouseAppService.class),
                mock(WarehouseService.class),mock(ReturnPhotoService.class),mock(PartialReturnService.class));
    }
    @Test void categoryTranslationsAndImagePersistClearAndPreserveLegacyEdits() throws Exception {
        tx.execute(s->service.createCategory(Map.of("name","Milk","name_uz","  Sut  ","name_ru"," Молоко ","image_url","products/test.png")));
        long id=jdbc.queryForObject("SELECT id FROM categories WHERE name='Milk'",Long.class);
        var mobile=catalog.getProductCategoryList().get(0);
        assertEquals("Milk",mobile.getCategoryName());assertEquals("Sut",mobile.getCategoryNameUz());
        assertEquals("Молоко",mobile.getCategoryNameRu());assertEquals("products/test.png",mobile.getCategoryImageUrl());

        tx.execute(s->service.updateCategory(id,Map.of("sort_order",3)));
        var row=jdbc.queryForMap("SELECT * FROM categories WHERE id=?",id);
        assertEquals("Sut",row.get("name_uz"));assertEquals("Молоко",row.get("name_ru"));assertEquals("products/test.png",row.get("image_url"));
        tx.execute(s->service.updateCategory(id,Map.of("name_ru"," ","image_url","")));
        assertNull(jdbc.queryForMap("SELECT name_ru FROM categories WHERE id=?",id).get("name_ru"));
        assertEquals("",jdbc.queryForObject("SELECT image_url FROM categories WHERE id=?",String.class,id));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.updateCategory(id,Map.of("image_url","javascript:alert(1)"))));
        assertEquals("Sut",jdbc.queryForObject("SELECT name_uz FROM categories WHERE id=?",String.class,id));
    }
    @Test void orderFiltersShareExactDateBoundariesCountsAndPagination() {
        jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,ordered_at) VALUES (90,1,'A','PENDING','PAID','2026-10-15 19:00:00'),(91,1,'B','DELIVERED','PAID','2026-10-16 18:59:59.999999'),(92,1,'C','PENDING','PAID','2026-10-16 19:00:00'),(93,1,'D','PENDING','FAILED','2026-10-16 12:00:00'),(94,1,'E','PENDING','PAID','2026-10-15 18:59:59.999999')");
        var all=service.getFilteredOrders(null,null,null,"2026-10-16","2026-10-16",1,20);
        assertEquals(3,all.get("total"));
        var first=service.getFilteredOrders("Test Customer",null,"PAID","2026-10-16","2026-10-16",1,1);
        var second=service.getFilteredOrders("Test Customer",null,"PAID","2026-10-16","2026-10-16",2,1);
        assertEquals(2,first.get("total"));
        assertEquals(91L,((Number)((List<CamelCaseMap>)first.get("items")).get(0).get("id")).longValue());
        assertEquals(90L,((Number)((List<CamelCaseMap>)second.get("items")).get(0).get("id")).longValue());
        assertEquals(1,service.getFilteredOrders("#90","PENDING","PAID","2026-10-16","2026-10-16",1,20).get("total"));
        assertEquals(0,service.getFilteredOrders("#90",null,"FAILED",null,null,1,20).get("total"));
        assertEquals(1,service.getFilteredOrders(null,null,null,null,"2026-10-15",1,20).get("total"));
    }
    @Test void shortSearchIsExactWhilePhoneNameAndOrdSearchRemainAvailable() {
        for(String query:List.of("87","#87","#000087")) assertEquals(1,mapper.countOrders(query,null,null,null,null),query);
        assertEquals(2,mapper.countOrders("998901234587",null,null,null,null));
        assertEquals(2,mapper.countOrders("Test Customer",null,null,null,null));
        assertEquals(1,mapper.countOrders("ORD-EXACT-87",null,null,null,null));
        assertEquals(0,mapper.countOrders("#87","DELIVERED",null,null,null));
        assertEquals(0,mapper.countOrders("#9999999999999999999999999",null,null,null,null));
    }
    @Test void categoryNamesTrimAndParentCanBeRemovedWithoutLosingOtherFields() {
        tx.execute(s->{service.createCategory(Map.of("name","  Parent  "));return null;});
        long parent=jdbc.queryForObject("SELECT id FROM categories WHERE name='Parent'",Long.class);
        tx.execute(s->{service.createCategory(Map.of("name"," Child ","parent_id",parent));return null;});
        long child=jdbc.queryForObject("SELECT id FROM categories WHERE name='Child'",Long.class);
        var update=new HashMap<String,Object>();update.put("parent_id",null);
        tx.execute(s->service.updateCategory(child,update));
        assertNull(jdbc.queryForMap("SELECT parent_id FROM categories WHERE id=?",child).get("parent_id"));
        assertEquals("Child",jdbc.queryForObject("SELECT name FROM categories WHERE id=?",String.class,child));
    }
    @Test void cycleAndBlankNameFailuresLeaveDatabaseUnchanged() {
        jdbc.update("INSERT INTO categories(id,name,parent_id) VALUES(1,'Parent',NULL),(2,'Child',1),(3,'Grandchild',2)");
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.updateCategory(1,Map.of("parent_id",3))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.createCategory(Map.of("name","  "))));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM categories",Integer.class));
        assertNull(jdbc.queryForMap("SELECT parent_id FROM categories WHERE id=1").get("parent_id"));
    }
    @Test void applicationSettingsMigrationCanRunTwiceAndSavePersists() {
        var script=new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new FileSystemResource("database/migrations/20261014_app_configs.sql"));
        script.execute(jdbc.getDataSource());script.execute(jdbc.getDataSource());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM app_configs",Integer.class));
        assertEquals("N",mapper.getSettings().get("maintenance_yn"));
        mapper.updateSettings(Map.of("maintenance_message","verification only"));
        assertEquals("verification only",mapper.getSettings().get("maintenance_message"));
        assertEquals("N",mapper.getSettings().get("force_update_yn"));
    }
}
