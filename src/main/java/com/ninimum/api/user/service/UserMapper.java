package com.ninimum.api.user.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.param.RegisterUserParam;
import com.ninimum.api.param.UpdateUserInfoParam;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    CamelCaseMap selectUserByPhone(String phone_number) throws Exception;

    int insertUser(RegisterUserParam param) throws Exception;

    int updateUserInfo(UpdateUserInfoParam param) throws Exception;
}