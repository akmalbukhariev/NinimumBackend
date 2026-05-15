package com.ninimum.api.user.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.CheckPhoneNumberDto;
import com.ninimum.api.dto.RefreshTokenDto;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.*;
import com.ninimum.api.security.jwt.JwtTokenProvider;
import com.ninimum.api.user.service.IUserService;
import com.ninimum.api.user.service.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final UserMapper mapper;
    private final JwtTokenProvider jwtTokenProvider;
    @Override
    public UserDto getUserByPhone(String phone_number) throws Exception {
        CamelCaseMap map = mapper.selectUserByPhone(phone_number);

        if (map != null) {
            return map.toObject(UserDto.class);
        }

        return null;
    }

    @Override
    public int register(RegisterUserParam param) throws Exception {
        return mapper.insertUser(param);
    }

    @Override
    public int updateUserInfo(UpdateUserInfoParam param) throws Exception {
        return mapper.updateUserInfo(param);
    }

    @Override
    public RefreshTokenDto refreshToken(RefreshTokenParam param) throws Exception {
        String savedRefreshToken = this.mapper.getRefreshToken(param);

        if (savedRefreshToken == null || !savedRefreshToken.equals(param.getRefreshToken())) {
            return null;
        }

        String newAccessToken = "";//jwtTokenProvider.createAccessToken(param.getUserId());
        String newRefreshToken = "";//jwtTokenProvider.createRefreshToken(param.getUserId());

        param.setRefreshToken(newRefreshToken);
        this.mapper.updateRefreshToken(param);

        RefreshTokenDto dto = new RefreshTokenDto();
        dto.setAccessToken(newAccessToken);
        dto.setRefreshToken(newRefreshToken);

        return dto;
    }

    @Override
    public int logout(LogoutParam param) throws Exception {
        return this.mapper.logout(param);
    }

    @Override
    public int deleteAccount(DeleteAccountParam param) throws Exception {
        return this.mapper.deleteAccount(param);
    }

    @Override
    public int changePassword(ChangePasswordParam param) throws Exception {
        String savedPassword = this.mapper.getUserPassword(param);

        if (savedPassword == null || !savedPassword.equals(param.getCurrentPassword())) {
            return 0;
        }

        return this.mapper.changePassword(param);
    }

    @Override
    public int forgotPassword(ForgotPasswordParam param) throws Exception {
        return this.mapper.forgotPassword(param);
    }

    @Override
    public int changePhoneNumber(ChangePhoneNumberParam param) throws Exception {
        return this.mapper.changePhoneNumber(param);
    }

    @Override
    public CheckPhoneNumberDto checkPhoneNumber(CheckPhoneNumberParam param) throws Exception {
        int count = this.mapper.checkPhoneNumber(param);

        CheckPhoneNumberDto dto = new CheckPhoneNumberDto();
        dto.setExistsYn(count > 0 ? "Y" : "N");

        return dto;
    }

    @Override
    public int updateDeviceToken(UpdateDeviceTokenParam param) throws Exception {
        return this.mapper.updateDeviceToken(param);
    }
}