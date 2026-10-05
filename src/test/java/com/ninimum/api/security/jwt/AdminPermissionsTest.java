package com.ninimum.api.security.jwt;

import com.ninimum.api.configure.SecurityConfig;
import com.ninimum.api.admin.service.AdminSessionService;
import com.ninimum.api.deliveryapp.service.DeliverySessionService;
import com.ninimum.api.security.AdminDetailsServiceImpl;
import com.ninimum.api.security.UserDetailsServiceImpl;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfig.class, AdminPermissionsTest.Fixture.class})
class AdminPermissionsTest {
    @Configuration @EnableWebMvc
    static class Fixture {
        @Bean JwtTokenProvider jwt() { return mock(JwtTokenProvider.class); }
        @Bean com.ninimum.api.warehouse.WarehouseAppService warehouseApp() { return mock(com.ninimum.api.warehouse.WarehouseAppService.class); }
        @Bean AdminSessionService admins() { return mock(AdminSessionService.class); }
        @Bean DeliverySessionService deliveries() { return mock(DeliverySessionService.class); }
        @Bean AdminDetailsServiceImpl details() { return mock(AdminDetailsServiceImpl.class); }
        @Bean UserDetailsServiceImpl users() { return mock(UserDetailsServiceImpl.class); }
        @Bean Endpoints endpoints() { return new Endpoints(); }
    }
    @RestController static class Endpoints {
        @RequestMapping({"/ninimum/api/v1/admin/register", "/ninimum/api/v1/admin/management/admins",
                "/ninimum/api/v1/admin/management/settings", "/ninimum/api/v1/admin/warehouse/locations", "/ninimum/api/v1/admin/warehouse/status", "/ninimum/api/v1/admin/management/categories",
                "/ninimum/api/v1/admin/management/orders", "/ninimum/api/v1/admin/management/products",
                "/ninimum/api/v1/admin/management/delivery/jobs", "/ninimum/api/v1/admin/me", "/ninimum/api/v1/warehouse-app/me", "/ninimum/api/v1/warehouse-app/login"})
        public String ok() { return "ok"; }
    }
    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider jwt;
    @Autowired com.ninimum.api.warehouse.WarehouseAppService warehouseApp;
    @Autowired AdminSessionService sessions;
    @Autowired AdminDetailsServiceImpl details;
    private MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", javax.servlet.Filter.class)).build(); }
    private void login(String role) throws Exception {
        Claims claims = Jwts.claims(); claims.setSubject("test"); claims.put("auth", role);
        when(jwt.validateToken("token")).thenReturn(Result.SUCCESS);
        when(jwt.parseClaims("token")).thenReturn(claims);
        when(jwt.getAuthentication("token")).thenReturn(new UsernamePasswordAuthenticationToken("test", null,
                Collections.singletonList(new SimpleGrantedAuthority(role))));
        when(sessions.isCurrent("test", "token")).thenReturn(true);
        CamelCaseMap profile = new CamelCaseMap(); profile.put("status", "ACTIVE"); profile.put("role", role);
        when(details.getAdminByLoginId("test")).thenReturn(profile);
    }
    @Test void restrictedAdminCannotManageAdminsOrSettings() throws Exception {
        login("ADMIN");
        for (String path : new String[]{"register", "management/admins", "management/settings", "warehouse/locations"})
            mvc.perform(get("/ninimum/api/v1/admin/" + path).header("Authorization", "Bearer token"))
                    .andExpect(status().isForbidden());
    }
    @Test void restrictedAdminCanUseOperationalModules() throws Exception {
        login("ADMIN");
        for (String path : new String[]{"orders", "products", "categories", "delivery/jobs"})
            mvc.perform(get("/ninimum/api/v1/admin/management/" + path).header("Authorization", "Bearer token"))
                    .andExpect(status().isOk());
    }
    @Test void ownerCanManageAdminsAndSettings() throws Exception {
        login("SUPER_ADMIN");
        mvc.perform(post("/ninimum/api/v1/admin/register").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(get("/ninimum/api/v1/admin/management/settings").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }
    @Test void ownerCanUseWarehouseAndRestrictedAdminCanReadItsStatus() throws Exception {
        login("SUPER_ADMIN");
        mvc.perform(get("/ninimum/api/v1/admin/warehouse/locations").header("Authorization","Bearer token"))
                .andExpect(status().isOk());
        login("ADMIN");
        mvc.perform(get("/ninimum/api/v1/admin/warehouse/status").header("Authorization","Bearer token"))
                .andExpect(status().isOk());
    }

    @Test void warehouseWorkerCanOnlyUseWarehouseAppAndReplacedSessionIsRejected() throws Exception {
        login("WAREHOUSE"); when(warehouseApp.isCurrent("test","token")).thenReturn(true);
        mvc.perform(get("/ninimum/api/v1/warehouse-app/me").header("Authorization","Bearer token")).andExpect(status().isOk());
        mvc.perform(get("/ninimum/api/v1/admin/warehouse/locations").header("Authorization","Bearer token")).andExpect(status().isForbidden());
        mvc.perform(get("/ninimum/api/v1/admin/management/orders").header("Authorization","Bearer token")).andExpect(status().isForbidden());
        when(warehouseApp.isCurrent("test","token")).thenReturn(false);
        mvc.perform(get("/ninimum/api/v1/warehouse-app/me").header("Authorization","Bearer token")).andExpect(status().isUnauthorized());
    }
    @Test void adminCannotUseWorkerApiAndWorkerLoginIsPublic() throws Exception {
        login("SUPER_ADMIN");
        mvc.perform(get("/ninimum/api/v1/warehouse-app/me").header("Authorization","Bearer token")).andExpect(status().isForbidden());
        mvc.perform(post("/ninimum/api/v1/warehouse-app/login")).andExpect(status().isOk());
    }
}
