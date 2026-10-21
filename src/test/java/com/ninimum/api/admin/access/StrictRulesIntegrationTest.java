package com.ninimum.api.admin.access;

import com.ninimum.api.warehouse.WarehouseException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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

/** M14: Q-rules defaults, owner/staff switches and their audit. Isolated access_test only. */
@EnabledIfSystemProperty(named="access.test.jdbc",matches=".*")
class StrictRulesIntegrationTest {
    JdbcTemplate jdbc;StrictRules rules;
    @BeforeEach void setup() {
        String url=System.getProperty("access.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/access_test"))throw new IllegalArgumentException("Dedicated local access_test only");
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"root",""));
        for(String table:List.of("strict_rules_audit","strict_rules"))jdbc.execute("DROP TABLE IF EXISTS "+table);
        rules=new StrictRules(jdbc);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void as(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner",null,List.of(new SimpleGrantedAuthority(role))));}
    void migrate(){var m=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261021_strict_rules.sql"));m.execute(jdbc.getDataSource());m.execute(jdbc.getDataSource());}

    @Test void withoutTheTableTheDefaultsKeepTodaysBehaviour() {
        as("ADMIN");assertTrue(StrictRules.on("Q-10"));assertTrue(StrictRules.on("Q-6"));assertFalse(StrictRules.on("Q-14"));
        as("SUPER_ADMIN");assertFalse(StrictRules.on("Q-10"));assertFalse(StrictRules.on("Q-6"));assertTrue(StrictRules.on("Q-9"));
        SecurityContextHolder.clearContext();assertTrue(StrictRules.on("Q-8"),"warehouse workers and couriers count as staff");
    }

    @Test void ownerSwitchesARuleSeparatelyForStaffAndOwnerAndItIsLogged() {
        migrate();as("SUPER_ADMIN");
        rules.update("Q-10",Map.of("staff_enabled",false,"owner_enabled",true));
        assertTrue(StrictRules.on("Q-10"));as("ADMIN");assertFalse(StrictRules.on("Q-10"));
        as("SUPER_ADMIN");
        var row=rules.list().stream().filter(r->"Q-10".equals(r.get("code"))).findFirst().orElseThrow();
        assertEquals(false,row.get("staff_enabled"));assertEquals(true,row.get("owner_enabled"));assertEquals("owner",row.get("updated_by"));
        assertEquals("staff=true owner=false",jdbc.queryForObject("SELECT old_value FROM strict_rules_audit WHERE code='Q-10'",String.class));
        assertEquals(18,rules.list().size());
    }

    @Test void onlyTheOwnerChangesRulesAndOnlySwitchableOnes() {
        migrate();as("ADMIN");
        assertThrows(AccessDeniedException.class,()->rules.list());
        assertThrows(AccessDeniedException.class,()->rules.update("Q-10",Map.of("staff_enabled",false)));
        as("SUPER_ADMIN");
        for(String code:List.of("Q-1","Q-3","Q-11","Q-99"))
            assertEquals("RULE_NOT_SWITCHABLE",assertThrows(WarehouseException.class,()->rules.update(code,Map.of("staff_enabled",false))).getMessage());
    }
}
