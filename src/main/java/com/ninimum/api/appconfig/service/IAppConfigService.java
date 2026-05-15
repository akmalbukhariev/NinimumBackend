package com.ninimum.api.appconfig.service;

import com.ninimum.api.dto.AppConfigDto;

public interface IAppConfigService {
    AppConfigDto getAppConfig() throws Exception;
}