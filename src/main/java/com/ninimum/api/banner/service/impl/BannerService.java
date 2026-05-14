package com.ninimum.api.banner.service.impl;

import com.ninimum.api.banner.service.BannerMapper;
import com.ninimum.api.banner.service.IBannerService;
import com.ninimum.api.dto.BannerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerService implements IBannerService {

    private final BannerMapper bannerMapper;

    @Override
    public List<BannerDto> getBannerList() throws Exception {
        return this.bannerMapper.getBannerList();
    }
}