package com.ninimum.api.banner.service;

import com.ninimum.api.dto.BannerDto;

import java.util.List;

public interface IBannerService {
    List<BannerDto> getBannerList() throws Exception;

    int insertBanner(BannerDto param) throws Exception;

    int deleteBanner(Long id) throws Exception;
}