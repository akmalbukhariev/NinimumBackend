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
        dto.setPassword(passwordEncoder.encode(com.ninimum.api.security.login.StaffPasswordPolicy.require(param.getPassword())));
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
        // M14: the web admin shows only the pages these permissions allow.
        profile.put("permissions", Constant.ROLE_SUPER_ADMIN.equals(found.get("role"))
                ? com.ninimum.api.admin.access.StaffAccess.ALL
                : com.ninimum.api.admin.access.StaffAccess.effective((String) found.get("staff_role"), (String) found.get("permissions_added"), (String) found.get("permissions_removed")));
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
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        // M14: money figures for the owner and for staff the owner gave the money permission.
        boolean owner=auth!=null&&com.ninimum.api.admin.access.StaffAccess.can(com.ninimum.api.admin.access.StaffAccess.MONEY);
        if(!owner){
            Map<String,Object> summary=new LinkedHashMap<>((Map<String,Object>)dashboard.get("summary"));
            for(String key:java.util.List.of("revenue","paid_undelivered","subscription_receipts","supplier_due_week"))summary.put(key,null);
            dashboard.put("summary",summary);dashboard.put("sales_last_7_days",java.util.List.of());
        }
        return dashboard;
    }
}
