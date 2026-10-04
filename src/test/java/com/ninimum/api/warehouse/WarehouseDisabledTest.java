package com.ninimum.api.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseDisabledTest {
    @Test void legacyPaymentsAndProductEditingDoNotRequireWarehouseTablesWhileDisabled() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        WarehouseService service=new WarehouseService(jdbc);
        assertFalse(service.isEnabled());
        service.verifySchema();
        service.recordChange(1,-2,"SALE","ORDER-1","SYSTEM","","sale:1");
        service.requireDocumentStock(Map.of("stock_quantity",13));
        assertEquals("WAREHOUSE_NOT_ENABLED",assertThrows(WarehouseException.class,service::locations).getMessage());
        verifyNoInteractions(jdbc);
    }
}
