package com.ninimum.api.version.service;

import com.ninimum.api.dto.VersionCheckDto;
import com.ninimum.api.param.VersionCheckParam;

public interface IVersionService {
    VersionCheckDto checkVersion(VersionCheckParam param) throws Exception;
}