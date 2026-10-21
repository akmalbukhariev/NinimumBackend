package com.ninimum.api.user.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.PhoneNumbers;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    /** M11: finds a customer by any stored form of the number; an exact match wins, then the E.164 row. */
    default CamelCaseMap selectUserByPhone(String phone_number) throws Exception {
        if (phone_number == null || phone_number.isBlank()) return null;
        return selectUserByPhoneForms(phone_number.trim(), PhoneNumbers.variants(phone_number));
    }

    CamelCaseMap selectUserByPhoneForms(@Param("phone") String phone, @Param("forms") List<String> forms) throws Exception;

    int countActiveUsersByPhoneForms(@Param("forms") List<String> forms, @Param("exceptUserId") Long exceptUserId) throws Exception;

    int insertUser(RegisterUserParam param) throws Exception;

    int updateUserInfo(UpdateUserInfoParam param) throws Exception;

    String getRefreshToken(RefreshTokenParam param) throws Exception;

    int updateRefreshToken(RefreshTokenParam param) throws Exception;

    int logout(LogoutParam param) throws Exception;

    int insertDeleteFeedback(DeleteAccountParam param) throws Exception;

    int deleteAccount(DeleteAccountParam param) throws Exception;
    String getUserPassword(ChangePasswordParam param) throws Exception;
    int changePassword(ChangePasswordParam param) throws Exception;
    int forgotPassword(ForgotPasswordParam param) throws Exception;
    int changePhoneNumber(ChangePhoneNumberParam param) throws Exception;
    default int countOtherUserByPhone(ChangePhoneNumberParam param) throws Exception {
        var forms = PhoneNumbers.variants(param.getPhoneNumber());
        return forms.isEmpty() ? 0 : countActiveUsersByPhoneForms(forms, param.getUserId());
    }
    default int checkPhoneNumber(CheckPhoneNumberParam param) throws Exception {
        var forms = PhoneNumbers.variants(param.getPhone_number());
        return forms.isEmpty() ? 0 : countActiveUsersByPhoneForms(forms, null);
    }
    int updateDeviceToken(UpdateDeviceTokenParam param) throws Exception;
    int updateUserStatusAndToken(UserDto dto) throws Exception;
    int changeRegion(ChangeRegionParam param) throws Exception;
}