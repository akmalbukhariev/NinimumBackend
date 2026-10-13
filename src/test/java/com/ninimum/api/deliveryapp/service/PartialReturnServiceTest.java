package com.ninimum.api.deliveryapp.service;
import com.ninimum.api.param.ReturnQuantityParam;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class PartialReturnServiceTest {
    private ReturnQuantityParam item(long id,int quantity) {
        var item=new ReturnQuantityParam();item.setProductId(id);item.setQuantity(quantity);return item;
    }
    @Test void twoOfFiveUsesOriginalPriceAndKeepsDeliveryFeeOutsideCalculation() {
        assertEquals(new BigDecimal("3000.00"),PartialReturnService.refund(new BigDecimal("7500"),new BigDecimal("7500"),new BigDecimal("7500"),5,2));
    }
    @Test void orderDiscountIsAllocatedToReturnedItems() {
        assertEquals(new BigDecimal("2700.00"),PartialReturnService.refund(new BigDecimal("6750"),new BigDecimal("7500"),new BigDecimal("7500"),5,2));
    }
    @Test void mixedProductsUseEachProductsSavedLineValue() {
        assertEquals(new BigDecimal("900.00"),PartialReturnService.refund(new BigDecimal("5400"),new BigDecimal("6000"),new BigDecimal("2000"),2,1));
    }
    @Test void duplicateProductZeroAndNegativeQuantitiesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->PartialReturnService.quantities(List.of(item(1,1),item(1,1))));
        assertThrows(IllegalArgumentException.class,()->PartialReturnService.quantities(List.of(item(1,0))));
        assertThrows(IllegalArgumentException.class,()->PartialReturnService.quantities(List.of(item(1,-1))));
        assertThrows(IllegalArgumentException.class,()->PartialReturnService.quantities(List.of()));
    }
}
