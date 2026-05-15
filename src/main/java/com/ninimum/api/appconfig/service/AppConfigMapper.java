package com.ninimum.api.appconfig.service;

import com.ninimum.api.dto.AppConfigDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppConfigMapper {
    AppConfigDto getAppConfig() throws Exception;
}