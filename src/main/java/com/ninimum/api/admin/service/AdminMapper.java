package com.ninimum.api.admin.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.AdminDto;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

@Mapper
public interface AdminMapper {

    CamelCaseMap getAdminByLoginId(@Param("login_id") String loginId);
    int registerAdmin(AdminDto adminDto);
}