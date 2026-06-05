package com.ninimum.api.message.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.EskizLoginInfoDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper {
    CamelCaseMap selectUser() throws Exception;

    int updateUserToken(EskizLoginInfoDto dto) throws Exception;
}
