package com.ninimum.api.user.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.RegisterUserParam;
import com.ninimum.api.param.UpdateUserInfoParam;
import com.ninimum.api.user.service.IUserService;
import com.ninimum.api.user.service.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final UserMapper mapper;

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
}