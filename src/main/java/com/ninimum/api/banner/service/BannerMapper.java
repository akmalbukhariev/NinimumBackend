package com.ninimum.api.banner.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.BannerDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BannerMapper {
    List<CamelCaseMap> getBannerList();

    int insertBannerList(List<BannerDto> param);

    int deleteBanner();
}