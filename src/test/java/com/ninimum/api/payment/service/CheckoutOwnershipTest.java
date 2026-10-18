package com.ninimum.api.payment.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.payme.CreatePaymeCheckoutUrlParam;
import com.ninimum.api.payment.service.impl.PaymeService;
import com.ninimum.api.warehouse.WarehouseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CheckoutOwnershipTest {
 @Test void onlyOwnerCanBuildCheckoutUrlAndNoPaymentIsExecuted() throws Exception {
  var mapper=mock(PaymeMapper.class);var warehouse=mock(WarehouseService.class);var order=new CamelCaseMap();
  order.put("user_id",7L);order.put("status","PENDING");order.put("payment_status","PENDING");order.put("total_price",1300);
  when(mapper.getPaymeOrderCheckById(any())).thenReturn(order);
  var service=new PaymeService(new ObjectMapper(),mapper,warehouse);
  ReflectionTestUtils.setField(service,"paymeMerchantId","test-only");ReflectionTestUtils.setField(service,"paymeCheckoutUrl","https://example.invalid/");ReflectionTestUtils.setField(service,"paymeReturnUrl","https://example.invalid/return");
  var request=new CreatePaymeCheckoutUrlParam();request.setOrder_id(87L);request.setUser_id(99L);
  assertThrows(IllegalStateException.class,()->service.createCheckoutUrl(request));
  request.setUser_id(null);assertThrows(IllegalStateException.class,()->service.createCheckoutUrl(request));
  request.setUser_id(7L);assertTrue(service.createCheckoutUrl(request).getPayment_url().startsWith("https://example.invalid/"));
  verify(mapper,times(3)).getPaymeOrderCheckById(any());verifyNoMoreInteractions(mapper);verifyNoInteractions(warehouse);
 }
}
