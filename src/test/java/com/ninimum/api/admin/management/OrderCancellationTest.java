package com.ninimum.api.admin.management;
import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.admin.management.service.impl.AdminManagementService;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.warehouse.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderCancellationTest {
 private AdminManagementService service(AdminManagementMapper mapper) {
  return new AdminManagementService(mapper,mock(FileService.class),mock(WarehouseAppService.class),mock(WarehouseService.class));
 }
 @Test void cancellationRequiresReasonBeforeAnyChange() {
  var mapper=mock(AdminManagementMapper.class);
  assertThrows(IllegalArgumentException.class,()->service(mapper).updateOrderStatus(52L,Map.of("status","CANCELLED")));
  verifyNoInteractions(mapper);
 }
 @Test void cancellationStopsDeliveryWithoutChangingPayment() {
  var mapper=mock(AdminManagementMapper.class);when(mapper.cancelOrder(52L,"Unavailable")).thenReturn(1);
  assertEquals(1,service(mapper).updateOrderStatus(52L,Map.of("status","CANCELLED","cancel_reason"," Unavailable ","payment_status","REFUNDED")));
  verify(mapper).cancelOrder(52L,"Unavailable");verify(mapper).syncDeliveryJobFromOrder(52L,"CANCELLED");verifyNoMoreInteractions(mapper);
 }
 @Test void rejectedCancellationDoesNotStopDelivery() {
  var mapper=mock(AdminManagementMapper.class);
  assertThrows(IllegalArgumentException.class,()->service(mapper).updateOrderStatus(52L,Map.of("status","CANCELLED","cancel_reason","Unavailable")));
  verify(mapper).cancelOrder(52L,"Unavailable");verifyNoMoreInteractions(mapper);
 }
}
