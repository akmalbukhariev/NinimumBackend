package com.ninimum.api.region.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.RegionDto;
import com.ninimum.api.region.service.IRegionService;
import com.ninimum.api.region.service.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionService implements IRegionService {

    private final RegionMapper mapper;

    @Override
    public List<RegionDto> getActiveRegions() throws Exception {
        List<CamelCaseMap> result = mapper.selectActiveRegions();

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .map(map -> map.toObject(RegionDto.class))
                .collect(Collectors.toList());
    }
}