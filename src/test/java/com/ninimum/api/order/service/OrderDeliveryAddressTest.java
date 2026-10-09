package com.ninimum.api.order.service;

import com.ninimum.api.dto.OrderDeliveryAddressDto;
import com.ninimum.api.dto.ProductCheckoutPriceDto;
import com.ninimum.api.order.service.impl.OrderService;
import com.ninimum.api.param.CreateOrderParam;
import com.ninimum.api.param.CreateOrderProductParam;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDeliveryAddressTest {
    private CreateOrderParam order() {
        var param = new CreateOrderParam();
        param.setUserId(7L);
        param.setAddressId(1L);
        var item = new CreateOrderProductParam();
        item.setProductId(12L); item.setQuantity(1);
        param.setProducts(List.of(item));
        return param;
    }
    private OrderMapper mapper() throws Exception {
        var mapper = mock(OrderMapper.class);
        var price = new ProductCheckoutPriceDto();
        price.setPrice(1000); price.setStockQuantity(5);
        when(mapper.getProductCheckoutPrice(12L)).thenReturn(price);
        when(mapper.createOrderAddress(any())).thenAnswer(inv -> {
            ((CreateOrderParam) inv.getArgument(0)).setAddressId(99L); return 1;
        });
        when(mapper.createOrder(any())).thenAnswer(inv -> {
            ((CreateOrderParam) inv.getArgument(0)).setOrderId(55L); return 1;
        });
        when(mapper.createOrderItem(any())).thenReturn(1);
        return mapper;
    }
    private void selected(CreateOrderParam param) {
        param.setDeliveryAddress("  Shahrisabz, selected street  ");
        param.setDeliveryLatitude(39.0578); param.setDeliveryLongitude(66.8342);
    }
    @Test void selectedAddressWinsOverProfileAndClientAddressId() throws Exception {
        var mapper = mapper(); var param = order(); selected(param);
        assertEquals(1, new OrderService(mapper).createOrder(param));
        assertEquals("Shahrisabz, selected street", param.getDeliveryAddress());
        assertEquals(99L, param.getAddressId());
        verify(mapper, never()).getUserDeliveryAddress(anyLong());
        var sequence = inOrder(mapper);
        sequence.verify(mapper).createOrderAddress(param);
        sequence.verify(mapper).createOrder(param);
        sequence.verify(mapper).createOrderItem(any());
    }
    @Test void oldClientCopiesItsOwnProfileAtCreation() throws Exception {
        var mapper = mapper(); var param = order();
        var profile = new OrderDeliveryAddressDto();
        profile.setDeliveryAddress("Shahrisabz, saved street");
        profile.setDeliveryLatitude(39.0600); profile.setDeliveryLongitude(66.8400);
        when(mapper.getUserDeliveryAddress(7L)).thenReturn(profile);
        new OrderService(mapper).createOrder(param);
        profile.setDeliveryAddress("Changed profile address");
        assertEquals("Shahrisabz, saved street", param.getDeliveryAddress());
        verify(mapper).getUserDeliveryAddress(7L);
    }
    @Test void incompleteSelectionDoesNotSilentlyUseProfile() throws Exception {
        var mapper = mapper(); var param = order(); selected(param);
        param.setDeliveryLongitude(null);
        assertThrows(Exception.class, () -> new OrderService(mapper).createOrder(param));
        verify(mapper, never()).getUserDeliveryAddress(anyLong());
        verify(mapper, never()).createOrderAddress(any());
        verify(mapper, never()).createOrder(any());
    }
    @Test void outsideCityAndNonFiniteCoordinatesAreRejected() throws Exception {
        for (double lat : new double[]{38.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            var mapper = mapper(); var param = order(); selected(param);
            param.setDeliveryLatitude(lat);
            assertThrows(Exception.class, () -> new OrderService(mapper).createOrder(param));
            verify(mapper, never()).createOrderAddress(any());
            verify(mapper, never()).createOrder(any());
        }
    }
    @Test void invalidLongitudeAndBlankAddressAreRejected() throws Exception {
        for (double lon : new double[]{64.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            var mapper = mapper(); var param = order(); selected(param);
            param.setDeliveryLongitude(lon);
            assertThrows(Exception.class, () -> new OrderService(mapper).createOrder(param));
            verify(mapper, never()).createOrderAddress(any());
        }
        var mapper = mapper(); var param = order(); selected(param);
        param.setDeliveryAddress(" ");
        assertThrows(Exception.class, () -> new OrderService(mapper).createOrder(param));
        verify(mapper, never()).createOrderAddress(any());
    }
    @Test void missingLegacyProfileRequiresAddressSelection() throws Exception {
        var mapper = mapper(); var param = order();
        assertThrows(Exception.class, () -> new OrderService(mapper).createOrder(param));
        verify(mapper, never()).createOrder(any());
    }
    @Test void addressInsertFailureStopsOrderCreation() throws Exception {
        var mapper = mapper(); var param = order(); selected(param);
        doReturn(0).when(mapper).createOrderAddress(any());
        assertThrows(Exception.class, () -> new OrderService(mapper).createOrder(param));
        verify(mapper, never()).createOrder(any());
    }
}
