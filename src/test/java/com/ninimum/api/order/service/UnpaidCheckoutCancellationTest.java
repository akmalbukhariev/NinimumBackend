package com.ninimum.api.order.service;

import com.ninimum.api.order.service.impl.OrderService;
import com.ninimum.api.param.CancelOrderParam;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnpaidCheckoutCancellationTest {
    @Test void checkoutCancellationUpdatesTheOrderInsteadOfDeletingIt() throws Exception {
        var config = new org.apache.ibatis.session.Configuration();
        config.getTypeAliasRegistry().registerAlias("camelMap", com.ninimum.api.camelcase.CamelCaseMap.class);
        String file = "mapper/Order/OrderMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(file)) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input, config, file, config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement("com.ninimum.api.order.service.OrderMapper.cancelUnpaidOrder");
        assertEquals(org.apache.ibatis.mapping.SqlCommandType.UPDATE, statement.getSqlCommandType());
        String sql = statement.getBoundSql(request()).getSql();
        assertTrue(sql.contains("status='CANCELLED'"));
        assertTrue(sql.contains("status_actor=CONCAT('CUSTOMER:'"));
        assertTrue(sql.contains("user_id = ?"));
        assertTrue(sql.contains("payment_status = 'PENDING'"));
    }
    private CancelOrderParam request() {
        var param = new CancelOrderParam();
        param.setOrderId(79L); param.setUserId(9L); param.setReason("Back from checkout");
        return param;
    }

    @Test void alreadyExpiredCheckoutCanExitSuccessfully() throws Exception {
        var mapper = mock(OrderMapper.class);
        var param = request();
        when(mapper.isAlreadyCancelledUnpaidOrder(param)).thenReturn(1);
        assertEquals(1, new OrderService(mapper).cancelUnpaidOrder(param));
        verify(mapper).isAlreadyCancelledUnpaidOrder(param);
        verifyNoMoreInteractionsAfterCancellation(mapper, param);
    }

    private void verifyNoMoreInteractionsAfterCancellation(OrderMapper mapper, CancelOrderParam param) throws Exception {
        verify(mapper).cancelUnpaidOrder(param);
        verifyNoMoreInteractions(mapper);
    }

    @Test void ineligibleOrOtherCustomersOrderStillRejectsCancellation() {
        var mapper = mock(OrderMapper.class);
        assertThrows(Exception.class, () -> new OrderService(mapper).cancelUnpaidOrder(request()));
    }

    @Test void normalPendingCheckoutKeepsExistingBackBehavior() throws Exception {
        var mapper = mock(OrderMapper.class);
        var param = request();
        when(mapper.cancelUnpaidOrder(param)).thenReturn(1);
        assertEquals(1, new OrderService(mapper).cancelUnpaidOrder(param));
        verify(mapper, never()).isAlreadyCancelledUnpaidOrder(any());
    }
}
