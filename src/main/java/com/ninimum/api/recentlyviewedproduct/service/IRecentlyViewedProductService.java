package com.ninimum.api.recentlyviewedproduct.service;

import com.ninimum.api.dto.RecentlyViewedProductDto;
import com.ninimum.api.param.AddRecentlyViewedProductParam;
import com.ninimum.api.param.RecentlyViewedProductListParam;

import java.util.List;

public interface IRecentlyViewedProductService {
    List<RecentlyViewedProductDto> getRecentlyViewedProductList(RecentlyViewedProductListParam param) throws Exception;

    int addRecentlyViewedProduct(AddRecentlyViewedProductParam param) throws Exception;
}