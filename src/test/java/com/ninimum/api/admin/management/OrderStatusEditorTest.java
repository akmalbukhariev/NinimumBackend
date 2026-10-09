package com.ninimum.api.admin.management;

import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.admin.management.service.impl.AdminManagementService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.warehouse.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderStatusEditorTest {
    private AdminManagementService service(AdminManagementMapper mapper) {
        return new AdminManagementService(mapper,mock(FileService.class),mock(WarehouseAppService.class),mock(WarehouseService.class));
    }
    private AdminManagementMapper mapper(String state) {
        var mapper=mock(AdminManagementMapper.class);
        var order=new CamelCaseMap();order.put("status",state);order.put("payment_status","PAID");
        when(mapper.lockOrderState(66L)).thenReturn(order);return mapper;
    }
    @Test void manualSkippingAndBackwardEditsAreRejected() {
        for (String[] change:new String[][]{{"CONFIRMED","DELIVERED"},{"PREPARING","PENDING"},{"CONFIRMED","ON_THE_WAY"},{"CANCELLED","CONFIRMED"}}) {
            var mapper=mapper(change[0]);
            assertThrows(IllegalArgumentException.class,()->service(mapper).updateOrderStatus(66L,Map.of("status",change[1])));
            verify(mapper).lockOrderState(66L);verifyNoMoreInteractions(mapper);
        }
    }
    @Test void unchangedStatusCannotRewritePayment() {
        var mapper=mapper("CONFIRMED");
        assertEquals(1,service(mapper).updateOrderStatus(66L,Map.of("status","CONFIRMED","payment_status","REFUNDED")));
        verify(mapper).lockOrderState(66L);verifyNoMoreInteractions(mapper);
    }
    @Test void missingOrderAndMissingStatusAreRejected() {
        var mapper=mock(AdminManagementMapper.class);
        assertThrows(IllegalArgumentException.class,()->service(mapper).updateOrderStatus(66L,Map.of("status","DELIVERED")));
        assertThrows(IllegalArgumentException.class,()->service(mapper).updateOrderStatus(66L,Map.of()));
    }
}
