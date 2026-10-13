package com.ninimum.api.payment.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.dto.payme.PaymeResponse;
import com.ninimum.api.payment.service.IPaymeService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RefundMonitorTest {
    final ObjectMapper json=new ObjectMapper();
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final PaymeReceiptClient client=mock(PaymeReceiptClient.class);
    final IPaymeService payme=mock(IPaymeService.class);
    RefundMonitor monitor() {
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new RefundMonitor(jdbc,client,payme,json,manager);
    }
    void setup(int state) throws Exception {
        when(client.available()).thenReturn(true);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id",9L,"order_id",73L,"provider_transaction_id","receipt")));
        when(client.receipt("receipt")).thenReturn(json.readTree("{\"_id\":\"receipt\",\"account\":{\"order_id\":73},\"state\":"+state+",\"amount\":130000,\"pay_time\":1000,\"cancel_time\":2000}"));
        when(jdbc.queryForMap(anyString(),eq(9L))).thenReturn(Map.of("status","PAID","amount_tiyin",130000,"provider_transaction_id","receipt"));
    }
    @Test void disabledMakesNoDatabaseOrProviderCalls() throws Exception { monitor().poll();verifyNoInteractions(jdbc,payme);verify(client,never()).receipt(anyString()); }
    @Test void queuedCancellationDoesNotMarkRefunded() throws Exception { setup(21);monitor().poll();verifyNoInteractions(payme); }
    @Test void confirmedRefundUsesExistingTransactionalCancellation() throws Exception {
        setup(50);when(payme.handleRequest(any())).thenReturn(PaymeResponse.success(Map.of(),1));monitor().poll();
        verify(payme).handleRequest(argThat(r -> r.getMethod().equals("CancelTransaction") && r.getParams().path("id").asText().equals("receipt")));
        verify(jdbc).update(eq("UPDATE payments SET payme_cancel_time=? WHERE id=?"),eq(2000L),eq(9L));
    }
    @Test void mismatchedAmountCannotChangePayment() throws Exception {
        setup(50);when(jdbc.queryForMap(anyString(),eq(9L))).thenReturn(Map.of("status","PAID","amount_tiyin",150000));
        monitor().poll();verifyNoInteractions(payme);
        verify(jdbc).update(contains("CHECK_FAILED"),eq(9L));
    }
    @Test void alreadyRefundedDoesNotRunCancellationAgain() throws Exception {
        setup(50);when(jdbc.queryForMap(anyString(),eq(9L))).thenReturn(Map.of("status","CANCELLED"));monitor().poll();verifyNoInteractions(payme);
    }
    @Test void providerFailureDoesNotChangePayment() throws Exception {
        setup(50);when(client.receipt("receipt")).thenThrow(new IllegalStateException("PROVIDER_ERROR"));monitor().poll();verifyNoInteractions(payme);
        verify(jdbc).update(contains("CHECK_FAILED"),eq(9L));
    }
    @Test void receiptMustBelongToExpectedOrder() throws Exception {
        var receipt=json.readTree("{\"_id\":\"receipt\",\"account\":{\"order_id\":72}}");
        assertThrows(IllegalStateException.class,()->RefundMonitor.validateReceipt(receipt,"receipt",73));
    }
}
