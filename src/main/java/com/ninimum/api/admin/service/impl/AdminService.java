package com.ninimum.api.admin.service.impl;

import com.ninimum.api.admin.service.AdminMapper;
import com.ninimum.api.admin.service.IAdminService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.AdminDto;
import com.ninimum.api.param.RegisterAdminParam;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService implements IAdminService {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public int registerAdmin(RegisterAdminParam param) throws Exception {
        CamelCaseMap existingAdmin = adminMapper.getAdminByLoginId(param.getLogin_id());
        if (existingAdmin != null) {
            return 0;
        }

        AdminDto dto = new AdminDto();
        dto.setLogin_id(param.getLogin_id());
        dto.setPassword(passwordEncoder.encode(param.getPassword()));
        dto.setName(param.getName());
        dto.setRole(Constant.ROLE_ADMIN);
        dto.setStatus("ACTIVE");

        return adminMapper.registerAdmin(dto);
    }

    @Override
    public CamelCaseMap getAdminProfile(String loginId) throws Exception {
        CamelCaseMap found = adminMapper.getAdminByLoginId(loginId);
        if (found == null) {
            return null;
        }

        CamelCaseMap profile = new CamelCaseMap();
        profile.putAll(found);
        profile.remove("password");
        return profile;
    }

    @Override
    public Map<String, Object> getDashboard() throws Exception {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("summary", adminMapper.getDashboardSummary());
        dashboard.put("sales_last_7_days", adminMapper.getDashboardSalesLast7Days());
        dashboard.put("order_status_today", adminMapper.getDashboardOrderStatusToday());
        dashboard.put("recent_orders", adminMapper.getDashboardRecentOrders());
        dashboard.put("low_stock_products", adminMapper.getDashboardLowStockProducts());
        return dashboard;
    }
}
