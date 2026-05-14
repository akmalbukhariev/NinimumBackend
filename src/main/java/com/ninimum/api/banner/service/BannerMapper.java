package com.ninimum.api.banner.service;

import com.ninimum.api.dto.BannerDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BannerMapper {
    List<BannerDto> getBannerList() throws Exception;
}