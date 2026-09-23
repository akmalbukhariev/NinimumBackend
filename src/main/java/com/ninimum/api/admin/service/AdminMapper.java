package com.ninimum.api.admin.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.AdminDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMapper {

    CamelCaseMap getAdminByLoginId(@Param("login_id") String loginId);
    int registerAdmin(AdminDto adminDto);

    CamelCaseMap getDashboardSummary();
    List<CamelCaseMap> getDashboardSalesLast7Days();
    List<CamelCaseMap> getDashboardOrderStatusToday();
    List<CamelCaseMap> getDashboardRecentOrders();
    List<CamelCaseMap> getDashboardLowStockProducts();
}
