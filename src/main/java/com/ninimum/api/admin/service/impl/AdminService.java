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
}