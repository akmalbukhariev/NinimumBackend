package com.ninimum.api.recentlyviewedproduct.service;

import com.ninimum.api.dto.RecentlyViewedProductDto;
import com.ninimum.api.param.AddRecentlyViewedProductParam;
import com.ninimum.api.param.RecentlyViewedProductListParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecentlyViewedProductService implements IRecentlyViewedProductService {

    private final RecentlyViewedProductMapper recentlyViewedProductMapper;

    @Override
    public List<RecentlyViewedProductDto> getRecentlyViewedProductList(RecentlyViewedProductListParam param) throws Exception {
        return this.recentlyViewedProductMapper.getRecentlyViewedProductList(param);
    }

    @Override
    @Transactional
    public int addRecentlyViewedProduct(AddRecentlyViewedProductParam param) throws Exception {
        this.recentlyViewedProductMapper.deleteRecentlyViewedProduct(param);
        return this.recentlyViewedProductMapper.addRecentlyViewedProduct(param);
    }
}