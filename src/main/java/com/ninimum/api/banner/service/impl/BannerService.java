package com.ninimum.api.banner.service.impl;

import com.ninimum.api.banner.service.BannerMapper;
import com.ninimum.api.banner.service.IBannerService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Converter;
import com.ninimum.api.dto.BannerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerService implements IBannerService {

    private final BannerMapper bannerMapper;

    @Value("${file.access.url}")
    private String fileAccessUrl;

    @Override
    public List<BannerDto> getBannerList() throws Exception {
        List<CamelCaseMap> mapBanners = bannerMapper.getBannerList();
        List<BannerDto> banners = Converter.mapToDtoList(mapBanners, BannerDto.class);

        for (BannerDto banner : banners) {
            if (banner.getImage_url() != null && !banner.getImage_url().isEmpty()) {
                banner.setImage_url(fileAccessUrl + "/" + banner.getImage_url());
            }
        }

        return banners;
    }

    @Override
    public int insertBannerList(List<BannerDto> param) throws Exception {
        return bannerMapper.insertBannerList(param);
    }

    @Override
    public int deleteBanner() throws Exception {
        return bannerMapper.deleteBanner();
    }
}