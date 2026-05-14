package com.ninimum.api.region.service;

import com.ninimum.api.dto.RegionDto;

import java.util.List;

public interface IRegionService {
    List<RegionDto> getActiveRegions() throws Exception;
}