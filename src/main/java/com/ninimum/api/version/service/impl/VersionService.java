package com.ninimum.api.version.service.impl;

import com.ninimum.api.dto.VersionCheckDto;
import com.ninimum.api.param.VersionCheckParam;
import com.ninimum.api.version.service.IVersionService;
import com.ninimum.api.version.service.VersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VersionService implements IVersionService {

    private final VersionMapper versionMapper;

    @Override
    public VersionCheckDto checkVersion(VersionCheckParam param) throws Exception {
        return this.versionMapper.checkVersion(param);
    }
}