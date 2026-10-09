package com.ninimum.api.order.service;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OrderAddressMappingTest {
    @Test void orderAndCourierMappingsLoadAndUseOrderSnapshot() throws Exception {
        var config = new Configuration();
        config.getTypeAliasRegistry().registerAlias("camelMap", com.ninimum.api.camelcase.CamelCaseMap.class);
        for (String file : new String[]{"mapper/Order/OrderMapper.xml", "mapper/DeliveryApp/DeliveryAppMapper.xml", "mapper/AdminManagement/AdminManagementMapper.xml"}) {
            try (var input = getClass().getClassLoader().getResourceAsStream(file)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, config, file, config.getSqlFragments()).parse();
            }
        }
        String orderSql = config.getMappedStatement("com.ninimum.api.order.service.OrderMapper.createOrder")
                .getBoundSql(new com.ninimum.api.param.CreateOrderParam()).getSql();
        assertTrue(orderSql.contains("delivery_address"));
        assertTrue(orderSql.contains("delivery_latitude"));
        for (String method : new String[]{"getActiveJobs", "getJobDetail"}) {
            String sql = config.getMappedStatement("com.ninimum.api.deliveryapp.service.DeliveryAppMapper." + method)
                    .getBoundSql(Map.of("workerId", 1L, "jobId", 1L)).getSql();
            assertTrue(sql.contains("COALESCE(o.delivery_latitude,u.location_latitude)"));
            assertTrue(sql.contains("COALESCE(o.delivery_longitude,u.location_longitude)"));
        }
    }
}
