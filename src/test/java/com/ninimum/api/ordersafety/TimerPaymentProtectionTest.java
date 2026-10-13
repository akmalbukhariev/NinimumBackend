package com.ninimum.api.ordersafety;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.payme.PaymeRequest;
import com.ninimum.api.payment.service.PaymeMapper;
import com.ninimum.api.payment.service.impl.PaymeService;
import com.ninimum.api.warehouse.WarehouseService;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TimerPaymentProtectionTest {
    @Test void paymentAfterExpiryCannotDeductStockOrMarkPaid() throws Exception {
        var json=new ObjectMapper();var mapper=mock(PaymeMapper.class);var stock=mock(WarehouseService.class);
        var payment=new CamelCaseMap();payment.put("status","CREATED");payment.put("order_id",73L);
        when(mapper.getPaymePaymentByTransactionId(any())).thenReturn(payment);
        var order=new CamelCaseMap();order.put("status","CANCELLED");order.put("payment_status","PENDING");
        when(mapper.lockOrderForPayment(any())).thenReturn(order);
        var request=new PaymeRequest();request.setId(1);request.setMethod("PerformTransaction");request.setParams(json.readTree("{\"id\":\"receipt\"}"));
        var response=new PaymeService(json,mapper,stock).handleRequest(request);
        assertNotNull(response.getError());verify(mapper,never()).decreaseProductStock(any());verify(mapper,never()).performPaymePayment(any());verify(mapper,never()).updateOrderPaymentStatus(any());
    }
    @Test void timerSettingsRequireOwnerEvenWhenCalledDirectly() {
        var service=new OrderTimersService(mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(org.springframework.transaction.PlatformTransactionManager.class));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.saveSettings(java.util.Map.of()));
    }
}
