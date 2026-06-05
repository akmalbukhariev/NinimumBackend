package com.ninimum.api.message.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestPhoneMapper {
    CamelCaseMap selectTestPhone(String phoneNumber) throws Exception;
}
