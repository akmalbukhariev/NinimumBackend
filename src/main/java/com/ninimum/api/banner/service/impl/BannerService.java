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
        return bannerMapper.getBannerList();
    }

    @Override
    public int insertBanner(BannerDto param) throws Exception {
        return bannerMapper.insertBanner(param);
    }

    @Override
    public int deleteBanner(Long id) throws Exception {
        return bannerMapper.deleteBanner(id);
    }
}