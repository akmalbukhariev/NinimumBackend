package com.ninimum.api.admin.management;

import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.admin.management.service.impl.AdminManagementService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.warehouse.WarehouseAppService;
import com.ninimum.api.warehouse.WarehouseService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPaymentDetailsTest {
    @Test void orderDetailsIncludeEveryPaymentAttemptWithoutMutatingPayments() {
        var mapper = mock(AdminManagementMapper.class);
        var service = new AdminManagementService(mapper, mock(FileService.class), mock(WarehouseAppService.class), mock(WarehouseService.class));
        var order = new CamelCaseMap(); order.put("id", 52L);
        var failed = new CamelCaseMap(); failed.put("status", "CANCELLED");
        var paid = new CamelCaseMap(); paid.put("provider_transaction_id", "payme-receipt"); paid.put("status", "PAID");
        var payments = List.of(paid, failed);
        var event = new CamelCaseMap(); event.put("to_status", "CONFIRMED");
        var history = List.of(event);
        when(mapper.getOrder(52L)).thenReturn(order);
        when(mapper.getOrderItems(52L)).thenReturn(List.of());
        when(mapper.getOrderPayments(52L)).thenReturn(payments);
        when(mapper.getOrderStatusHistory(52L)).thenReturn(history);
        var result = service.getOrder(52L);
        assertSame(order, result.get("order"));
        assertSame(payments, result.get("payments"));
        assertSame(history, result.get("status_history"));
        verify(mapper).getOrder(52L);
        verify(mapper).getOrderItems(52L);
        verify(mapper).getOrderPayments(52L);
        verify(mapper).getOrderStatusHistory(52L);
        verifyNoMoreInteractions(mapper);
    }
    @Test void unpaidOrderHasAnEmptyPaymentList() {
        var mapper = mock(AdminManagementMapper.class);
        var service = new AdminManagementService(mapper, mock(FileService.class), mock(WarehouseAppService.class), mock(WarehouseService.class));
        when(mapper.getOrderPayments(52L)).thenReturn(List.of());
        assertEquals(List.of(), service.getOrder(52L).get("payments"));
    }
}
