package com.ninimum.api.appconfig.service.impl;

import com.ninimum.api.appconfig.service.AppConfigMapper;
import com.ninimum.api.appconfig.service.IAppConfigService;
import com.ninimum.api.dto.AppConfigDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppConfigService implements IAppConfigService {

    private final AppConfigMapper appConfigMapper;

    @Override
    public AppConfigDto getAppConfig() throws Exception {
        return this.appConfigMapper.getAppConfig();
    }
}