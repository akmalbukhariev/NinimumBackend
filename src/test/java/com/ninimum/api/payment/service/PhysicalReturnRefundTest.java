package com.ninimum.api.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.payme.PaymeRequest;
import com.ninimum.api.payment.service.impl.PaymeService;
import com.ninimum.api.warehouse.WarehouseService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhysicalReturnRefundTest {
    @Test void refundUpdatesPaymentWithoutRestockingPhysicalReturn() throws Exception {
        var mapper=mock(PaymeMapper.class);var warehouse=mock(WarehouseService.class);
        var payment=new CamelCaseMap();payment.put("status","PAID");payment.put("order_id",73L);
        when(mapper.getPaymePaymentByTransactionId(any())).thenReturn(payment);
        when(mapper.cancelPaymePayment(any())).thenReturn(1);
        when(warehouse.needsPhysicalReturn(73L)).thenReturn(true);
        var json=new ObjectMapper();var request=new PaymeRequest();
        request.setMethod("CancelTransaction");request.setId(1);request.setParams(json.readTree("{\"id\":\"return-payment\"}"));
        var response=new PaymeService(json,mapper,warehouse).handleRequest(request);
        assertNull(response.getError());assertNotNull(response.getResult());
        verify(mapper).updateOrderPaymentStatus(argThat(p->p.getOrder_id()==73L && "REFUNDED".equals(p.getPayment_status())));
        verify(mapper,never()).restoreOrderStock(any());
        verify(mapper,never()).getOrderStockItems(any());
        verify(warehouse,never()).recordChange(anyLong(),anyLong(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void ordinaryCancellationRetainsExistingRestockBehavior() throws Exception {
        var mapper=mock(PaymeMapper.class);var warehouse=mock(WarehouseService.class);
        var payment=new CamelCaseMap();payment.put("status","PAID");payment.put("order_id",74L);
        when(mapper.getPaymePaymentByTransactionId(any())).thenReturn(payment);
        when(mapper.cancelPaymePayment(any())).thenReturn(1);
        when(mapper.getOrderStockItems(any())).thenReturn(java.util.List.of());
        var json=new ObjectMapper();var request=new PaymeRequest();request.setMethod("CancelTransaction");
        request.setParams(json.readTree("{\"id\":\"ordinary-payment\"}"));
        assertNull(new PaymeService(json,mapper,warehouse).handleRequest(request).getError());
        verify(mapper).restoreOrderStock(any());
    }
}
