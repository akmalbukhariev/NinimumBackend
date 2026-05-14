package com.ninimum.api.user.service;

import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.RegisterUserParam;
import com.ninimum.api.param.UpdateUserInfoParam;

public interface IUserService {
    UserDto getUserByPhone(String phone_number) throws Exception;

    int register(RegisterUserParam param) throws Exception;
    int updateUserInfo(UpdateUserInfoParam param) throws Exception;
}