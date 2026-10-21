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

/** M14: staff role migration and the owner's role/permission changes. Isolated access_test only. */
@EnabledIfSystemProperty(named="access.test.jdbc",matches=".*")
class StaffAccessServiceIntegrationTest {
    JdbcTemplate jdbc;StaffAccessService service;
    @BeforeEach void setup() {
        String url=System.getProperty("access.test.jdbc");
        if(!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/access_test"))throw new IllegalArgumentException("Dedicated local access_test only");
        var ds=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(ds);
        for(String table:List.of("admin_access_audit","admin_users"))jdbc.execute("DROP TABLE IF EXISTS "+table);
        jdbc.execute("CREATE TABLE admin_users(id BIGINT AUTO_INCREMENT PRIMARY KEY,login_id VARCHAR(50),password VARCHAR(255),name VARCHAR(100),role VARCHAR(20) DEFAULT 'ADMIN',status VARCHAR(20))");
        jdbc.update("INSERT INTO admin_users(id,login_id,password,name,role,status) VALUES(1,'owner','x','Owner','SUPER_ADMIN','ACTIVE'),(2,'staff','x','Staff','ADMIN','ACTIVE')");
        var migration=new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261021_staff_roles.sql"));
        migration.execute(ds);migration.execute(ds);
        service=new StaffAccessService(jdbc);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void as(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner",null,List.of(new SimpleGrantedAuthority(role))));}

    @SuppressWarnings("unchecked") List<Map<String,Object>> admins(){return (List<Map<String,Object>>)service.overview().get("admins");}

    @Test void migrationKeepsExistingStaffAsRestrictedAdmins() {
        as("SUPER_ADMIN");
        var staff=admins().get(1);
        assertEquals("ADMIN",staff.get("staff_role"));
        assertEquals(StaffAccess.effective("ADMIN",null,null),staff.get("permissions"));
        assertEquals(StaffAccess.ALL,admins().get(0).get("permissions"));
    }

    @Test void ownerSetsRoleAndSinglePermissionsAndEveryChangeIsLogged() {
        as("SUPER_ADMIN");
        var wanted=new ArrayList<>(StaffAccess.effective("OPERATOR",null,null));wanted.remove("orders.edit");wanted.add("inventory.view");
        var result=service.update(2,Map.of("staff_role","OPERATOR","permissions",wanted));
        @SuppressWarnings("unchecked") var permissions=(Set<String>)result.get("permissions");
        assertTrue(permissions.contains("inventory.view"));assertFalse(permissions.contains("orders.edit"));assertTrue(permissions.contains("orders.view"));
        var row=jdbc.queryForMap("SELECT staff_role,permissions_added,permissions_removed FROM admin_users WHERE id=2");
        assertEquals("OPERATOR",row.get("staff_role"));assertEquals("inventory.view",row.get("permissions_added"));assertEquals("orders.edit",row.get("permissions_removed"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_access_audit WHERE admin_id=2 AND changed_by='owner'",Integer.class));
    }

    @Test void onlyTheOwnerChangesAccessAndTheOwnerAccountIsFixed() {
        as("ADMIN");
        assertThrows(AccessDeniedException.class,()->service.overview());
        assertThrows(AccessDeniedException.class,()->service.update(2,Map.of("staff_role","DIRECTOR","permissions",List.of())));
        as("SUPER_ADMIN");
        assertEquals("ADMIN_OWNER_ACCESS_FIXED",assertThrows(WarehouseException.class,()->service.update(1,Map.of("staff_role","OBSERVER","permissions",List.of()))).getMessage());
        assertEquals("ADMIN_INVALID_ROLE",assertThrows(WarehouseException.class,()->service.update(2,Map.of("staff_role","BOSS","permissions",List.of()))).getMessage());
        assertEquals("ADMIN_INVALID_PERMISSION",assertThrows(WarehouseException.class,()->service.update(2,Map.of("staff_role","OBSERVER","permissions",List.of("money.steal")))).getMessage());
    }
}
