package com.ninimum.api.version.service;

import com.ninimum.api.dto.VersionCheckDto;
import com.ninimum.api.param.VersionCheckParam;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VersionMapper {
    VersionCheckDto checkVersion(VersionCheckParam param) throws Exception;
}