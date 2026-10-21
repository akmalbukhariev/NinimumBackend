package com.ninimum.api.admin.management;

import com.ninimum.api.admin.management.service.CustomerMergeService;
import com.ninimum.api.param.CheckPhoneNumberParam;
import com.ninimum.api.user.service.UserMapper;
import com.ninimum.api.warehouse.WarehouseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** M11: E.164 migration, lookups by any phone form, and the owner merge. Isolated phone_test only. */
@EnabledIfSystemProperty(named="phone.test.jdbc",matches=".*")
class CustomerPhoneIntegrationTest {
    JdbcTemplate jdbc;DriverManagerDataSource ds;UserMapper users;CustomerMergeService merges;

    @BeforeEach void setup() throws Exception {
        String url=System.getProperty("phone.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/phone_test"))throw new IllegalArgumentException("Dedicated local phone_test only");
        ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for(String table:List.of("customer_merges","cart_items","favorites","orders","users"))jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        // users as in production (ninimum_schema_2026-10-17.sql), without the region FK.
        jdbc.execute("CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY,region_id BIGINT NOT NULL DEFAULT 1,first_name VARCHAR(100) NOT NULL,last_name VARCHAR(100),phone_number VARCHAR(20) NOT NULL,"+
                "password VARCHAR(255) NOT NULL DEFAULT 'x',is_phone_verified TINYINT(1) NOT NULL DEFAULT 0,is_active TINYINT(1) NOT NULL DEFAULT 1,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"+
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,status ENUM('ACTIVE','BANNED','DELETED') NOT NULL DEFAULT 'ACTIVE',UNIQUE KEY phone_number(phone_number))");
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,status VARCHAR(30),CONSTRAINT fk_orders_user FOREIGN KEY(user_id) REFERENCES users(id))");
        jdbc.execute("CREATE TABLE cart_items(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,product_id BIGINT NOT NULL,quantity INT,UNIQUE KEY uq_cart_user_product(user_id,product_id))");
        jdbc.execute("CREATE TABLE favorites(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,product_id BIGINT NOT NULL,UNIQUE KEY uq_favorites_user_product(user_id,product_id))");
        jdbc.update("INSERT INTO users(id,first_name,phone_number) VALUES(1,'Old form','992064790'),(2,'E164 form','+998992064790'),(3,'Twelve digits','998901112233'),(4,'Nine digits','901234567'),(5,'Spaced','+998 93 555 44 33'),(6,'Foreign','+79161234567')");
        var migration=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261020_phone_e164.sql"));
        migration.execute(ds);migration.execute(ds);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
        factory.setConfigLocation(new ClassPathResource("config/mapper-config.xml"));factory.setMapperLocations(new ClassPathResource("mapper/User/UserMapper.xml"));
        users=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(UserMapper.class);
        merges=new CustomerMergeService(jdbc);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void as(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner",null,List.of(new SimpleGrantedAuthority(role))));}
    String phone(long id){return jdbc.queryForObject("SELECT phone_number FROM users WHERE id=?",String.class,id);}
    long found(String typed) throws Exception {var u=users.selectUserByPhone(typed);return u==null?0:((Number)u.get("id")).longValue();}

    @Test void migrationStoresE164AndLeavesDuplicatesForTheOwner() {
        assertEquals("+998901112233",phone(3));assertEquals("+998901234567",phone(4));assertEquals("+998935554433",phone(5));
        assertEquals("+79161234567",phone(6));
        assertEquals("992064790",phone(1));assertEquals("+998992064790",phone(2));
        var groups=merges.duplicates();
        assertEquals(1,groups.size());assertEquals("+998992064790",groups.get(0).get("phone"));
    }

    @Test void lookupsMatchAnyFormAndPreferTheExactAccount() throws Exception {
        assertEquals(4,found("998901234567"));assertEquals(4,found("901234567"));assertEquals(3,found("+998 90 111 22 33"));
        // An existing session token carries the stored value, so the exact account must win over its duplicate.
        assertEquals(1,found("992064790"));assertEquals(2,found("+998992064790"));assertEquals(2,found("998992064790"));
        assertEquals(0,found("+998000000000"));assertEquals(0,found(" "));
        var check=new CheckPhoneNumberParam();check.setPhone_number("998901234567");assertEquals(1,users.checkPhoneNumber(check));
        check.setPhone_number("992064790");assertEquals(2,users.checkPhoneNumber(check));
    }

    @Test void ownerMergeMovesEverythingAndClosesTheOtherAccount() throws Exception {
        jdbc.update("INSERT INTO orders VALUES(10,1,'DELIVERED'),(11,2,'PENDING')");
        jdbc.update("INSERT INTO cart_items(user_id,product_id,quantity) VALUES(1,7,1),(1,8,2),(2,7,5)");
        jdbc.update("INSERT INTO favorites(user_id,product_id) VALUES(1,7)");
        as("SUPER_ADMIN");
        var result=merges.merge(2,1,"Same person, two phone formats");
        assertEquals(Map.of("orders",1,"cart_items",1,"favorites",1),result.get("moved"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=2",Integer.class));
        assertEquals(5,jdbc.queryForObject("SELECT quantity FROM cart_items WHERE user_id=2 AND product_id=7",Integer.class));
        var closed=jdbc.queryForMap("SELECT phone_number,is_active,status FROM users WHERE id=1");
        assertEquals("merged-1",closed.get("phone_number"));assertEquals(Boolean.FALSE,closed.get("is_active"));assertEquals("DELETED",closed.get("status"));
        var log=jdbc.queryForMap("SELECT kept_user_id,merged_phone,reason,merged_by FROM customer_merges WHERE merged_user_id=1");
        assertEquals(2L,((Number)log.get("kept_user_id")).longValue());assertEquals("992064790",log.get("merged_phone"));assertEquals("owner",log.get("merged_by"));
        assertEquals(2,found("992064790"));assertTrue(merges.duplicates().isEmpty());
        assertThrows(WarehouseException.class,()->merges.merge(2,1,"Again"));
    }

    @Test void keptAccountTakesTheE164FormWhenItHadTheOldForm() throws Exception {
        as("SUPER_ADMIN");
        merges.merge(1,2,"Keep the older account");
        assertEquals("+998992064790",phone(1));assertEquals("merged-2",phone(2));
    }

    @Test void mergeIsOwnerOnlyAndNeedsTheSameNumberAndAReason() {
        as("ADMIN");
        assertThrows(AccessDeniedException.class,()->merges.merge(2,1,"reason"));
        as("SUPER_ADMIN");
        assertEquals("CUSTOMER_MERGE_PHONE_MISMATCH",assertThrows(WarehouseException.class,()->merges.merge(2,3,"reason")).getMessage());
        assertEquals("CUSTOMER_MERGE_REASON_REQUIRED",assertThrows(WarehouseException.class,()->merges.merge(2,1," ")).getMessage());
        assertEquals("CUSTOMER_MERGE_INVALID",assertThrows(WarehouseException.class,()->merges.merge(2,2,"reason")).getMessage());
    }
}
