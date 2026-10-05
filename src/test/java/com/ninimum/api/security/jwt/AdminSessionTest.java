package com.ninimum.api.security.jwt;

import com.ninimum.api.admin.service.AdminSessionService;
import com.ninimum.api.deliveryapp.service.DeliverySessionService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.security.provider.AdminAuthenticationProvider;
import com.ninimum.api.security.provider.UserAuthenticationProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSessionTest {
    @Test void everyAdminLoginHasAUniqueToken() {
        JwtTokenProvider provider = new JwtTokenProvider(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        for (String role : new String[]{"ADMIN", "SUPER_ADMIN"}) {
            var auth = new UsernamePasswordAuthenticationToken("test", null,
                    Collections.singletonList(new SimpleGrantedAuthority(role)));
            assertNotEquals(provider.generateToken(auth).getAccessToken(), provider.generateToken(auth).getAccessToken());
        }
    }

    private void check(String tokenRole, String databaseRole, boolean current, boolean allowed) throws Exception {
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        AdminSessionService sessions = mock(AdminSessionService.class);
        AdminAuthenticationProvider admins = mock(AdminAuthenticationProvider.class);
        Claims claims = Jwts.claims(); claims.setSubject("test"); claims.put("auth", tokenRole);
        when(jwt.validateToken("token")).thenReturn(Result.SUCCESS);
        when(jwt.parseClaims("token")).thenReturn(claims);
        CamelCaseMap profile = new CamelCaseMap(); profile.put("status", "ACTIVE"); profile.put("role", databaseRole);
        when(admins.getAdminByLoginId("test")).thenReturn(profile);
        when(sessions.isCurrent("test", "token")).thenReturn(current);
        when(jwt.getAuthentication("token")).thenReturn(new UsernamePasswordAuthenticationToken("test", null,
                Collections.singletonList(new SimpleGrantedAuthority(tokenRole))));
        var filter = new JwtAuthenticationFilter(jwt, sessions, mock(DeliverySessionService.class), mock(com.ninimum.api.warehouse.WarehouseAppService.class),
                mock(UserAuthenticationProvider.class), admins);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/ninimum/api/v1/admin/me"); request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse(); MockFilterChain chain = new MockFilterChain();
        try {
            filter.doFilter(request, response, chain);
            if (allowed) assertNotNull(chain.getRequest());
            else {
                assertNull(chain.getRequest()); assertEquals(401, response.getStatus());
                assertTrue(response.getContentAsString().contains("ADMIN_SESSION_REPLACED"));
            }
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void previousLoginIsRejected() throws Exception { check("ADMIN", "ADMIN", false, false); }
    @Test void currentRestrictedSessionIsAccepted() throws Exception { check("ADMIN", "ADMIN", true, true); }
    @Test void currentOwnerSessionIsAccepted() throws Exception { check("SUPER_ADMIN", "SUPER_ADMIN", true, true); }
    @Test void demotedOwnerTokenIsRejected() throws Exception { check("SUPER_ADMIN", "ADMIN", true, false); }
}
