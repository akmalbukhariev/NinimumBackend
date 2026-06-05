package com.ninimum.api.admin.service;

import com.ninimum.api.param.RegisterAdminParam;

public interface IAdminService {
    int registerAdmin(RegisterAdminParam param) throws Exception;
}