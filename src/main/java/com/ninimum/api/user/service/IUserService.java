package com.ninimum.api.user.service;

import com.ninimum.api.dto.CheckPhoneNumberDto;
import com.ninimum.api.dto.RefreshTokenDto;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.*;

public interface IUserService {
    UserDto getUserByPhone(String phone_number) throws Exception;
    int register(RegisterUserParam param) throws Exception;
    int updateUserInfo(UpdateUserInfoParam param) throws Exception;
    RefreshTokenDto refreshToken(RefreshTokenParam param) throws Exception;
    int logout(LogoutParam param) throws Exception;
    int deleteAccount(DeleteAccountParam param) throws Exception;
    int changePassword(ChangePasswordParam param) throws Exception;
    int forgotPassword(ForgotPasswordParam param) throws Exception;
    int changePhoneNumber(ChangePhoneNumberParam param) throws Exception;
    CheckPhoneNumberDto checkPhoneNumber(CheckPhoneNumberParam param) throws Exception;
    int updateDeviceToken(UpdateDeviceTokenParam param) throws Exception;
}