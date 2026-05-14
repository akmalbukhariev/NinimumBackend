package com.ninimum.api.region.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RegionMapper {
    List<CamelCaseMap> selectActiveRegions() throws Exception;
}