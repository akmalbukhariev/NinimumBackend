package com.ninimum.api.user.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    CamelCaseMap selectUserByPhone(String phone_number) throws Exception;

    int insertUser(RegisterUserParam param) throws Exception;

    int updateUserInfo(UpdateUserInfoParam param) throws Exception;

    String getRefreshToken(RefreshTokenParam param) throws Exception;

    int updateRefreshToken(RefreshTokenParam param) throws Exception;

    int logout(LogoutParam param) throws Exception;

    int deleteAccount(DeleteAccountParam param) throws Exception;
    String getUserPassword(ChangePasswordParam param) throws Exception;
    int changePassword(ChangePasswordParam param) throws Exception;
    int forgotPassword(ForgotPasswordParam param) throws Exception;
    int changePhoneNumber(ChangePhoneNumberParam param) throws Exception;
    int checkPhoneNumber(CheckPhoneNumberParam param) throws Exception;
    int updateDeviceToken(UpdateDeviceTokenParam param) throws Exception;
}