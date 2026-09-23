package com.ninimum.api.admin.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.param.RegisterAdminParam;

import java.util.Map;

public interface IAdminService {
    int registerAdmin(RegisterAdminParam param) throws Exception;
    CamelCaseMap getAdminProfile(String loginId) throws Exception;
    Map<String, Object> getDashboard() throws Exception;
}
