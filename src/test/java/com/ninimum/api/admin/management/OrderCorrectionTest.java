package com.ninimum.api.admin.management;

import com.ninimum.api.admin.management.service.OrderCorrectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCorrectionTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void login(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner","",List.of(new SimpleGrantedAuthority(role))));}
    @Test void administratorsCustomersCouriersAndAnonymousCannotCorrect(){
        for(String role:List.of("ADMIN","USER","DELIVERY","WAREHOUSE")){
            login(role);var jdbc=mock(JdbcTemplate.class);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->new OrderCorrectionService(jdbc).correct(1,Map.of("status","CONFIRMED","reason","test")));
            verifyNoInteractions(jdbc);
        }
        clear();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->new OrderCorrectionService(mock(JdbcTemplate.class)).correct(1,Map.of()));
    }
    @Test void missingOrExcessiveReasonIsRejectedBeforeDatabaseWrites(){
        login("SUPER_ADMIN");var jdbc=mock(JdbcTemplate.class);
        for(String reason:List.of("","   ","x".repeat(501)))
            assertThrows(IllegalArgumentException.class,()->new OrderCorrectionService(jdbc).correct(1,Map.of("reason",reason)));
        verifyNoInteractions(jdbc);
    }
    @Test void staleForwardUnpaidAndCancelledCorrectionsAreRejected(){
        login("SUPER_ADMIN");
        for(String[] state: new String[][]{{"READY","PAID","PREPARING","CONFIRMED"},{"CONFIRMED","PAID","CONFIRMED","DELIVERED"},{"CANCELLED","PAID","CANCELLED","CONFIRMED"},{"READY","REFUNDED","READY","CONFIRMED"}}){
            var jdbc=mock(JdbcTemplate.class);
            when(jdbc.queryForList("SELECT status,payment_status FROM orders WHERE id=? FOR UPDATE",1L)).thenReturn(List.of(Map.of("status",state[0],"payment_status",state[1])));
            assertThrows(IllegalArgumentException.class,()->new OrderCorrectionService(jdbc).correct(1,Map.of("expected_status",state[2],"status",state[3],"reason","test")));
            verify(jdbc).queryForList("SELECT status,payment_status FROM orders WHERE id=? FOR UPDATE",1L);verifyNoMoreInteractions(jdbc);
        }
    }
}
