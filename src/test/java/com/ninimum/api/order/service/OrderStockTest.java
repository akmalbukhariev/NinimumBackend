package com.ninimum.api.order.service;

import com.ninimum.api.dto.ProductCheckoutPriceDto;
import com.ninimum.api.order.service.impl.OrderService;
import com.ninimum.api.order.controller.OrderController;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.param.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderStockTest {
    private CreateOrderParam order(int... quantities) {
        CreateOrderParam param = new CreateOrderParam();
        param.setUserId(1L); param.setAddressId(1L);
        param.setProducts(Arrays.stream(quantities).mapToObj(q -> {
            CreateOrderProductParam item = new CreateOrderProductParam();
            item.setProductId(7L); item.setQuantity(q); return item;
        }).collect(java.util.stream.Collectors.toList()));
        return param;
    }
    private OrderMapper mapper(int stock) throws Exception {
        OrderMapper mapper = mock(OrderMapper.class);
        ProductCheckoutPriceDto price = new ProductCheckoutPriceDto();
        price.setPrice(1000); price.setStockQuantity(stock);
        when(mapper.getProductCheckoutPrice(7L)).thenReturn(price);
        return mapper;
    }
    @Test void soldOutStopsBeforeOrderInsert() throws Exception {
        OrderMapper mapper = mapper(0);
        StockUnavailableException ex = assertThrows(StockUnavailableException.class,
            () -> new OrderService(mapper).createOrder(order(1)));
        assertEquals("Uzr, bu mahsulot qolmagan.", ex.getMessage());
        verify(mapper, never()).createOrder(any());
    }
    @Test void duplicateLinesAreCheckedTogether() throws Exception {
        OrderMapper mapper = mapper(3);
        StockUnavailableException ex = assertThrows(StockUnavailableException.class,
            () -> new OrderService(mapper).createOrder(order(2, 2)));
        assertEquals(3, ex.getAvailable());
        verify(mapper, never()).createOrder(any());
    }
    @Test void unavailableProductStopsBeforeOrderInsert() throws Exception {
        OrderMapper mapper = mock(OrderMapper.class);
        assertThrows(StockUnavailableException.class,
            () -> new OrderService(mapper).createOrder(order(1)));
        verify(mapper, never()).createOrder(any());
    }
    @Test void controllerPreservesSoldOutMessage() throws Exception {
        IOrderService service = mock(IOrderService.class);
        when(service.createOrder(any())).thenThrow(new StockUnavailableException(7L, 0));
        VersionResponseResult result = (VersionResponseResult)
            new OrderController(service).createOrder(order(1)).getBody();
        assertNotNull(result);
        assertEquals("STOCK_UNAVAILABLE", result.getResultCode());
        assertEquals(0, ((StockUnavailableResponse) result).getAvailableQuantity());
        assertEquals(7L, ((StockUnavailableResponse) result).getUnavailableProductId());
        assertEquals("Uzr, bu mahsulot qolmagan.", result.getResultMsg());
    }
}
