package com.ninimum.api.recentlyviewedproduct.service;

import com.ninimum.api.dto.RecentlyViewedProductDto;
import com.ninimum.api.param.AddRecentlyViewedProductParam;
import com.ninimum.api.param.RecentlyViewedProductListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RecentlyViewedProductMapper {
    List<RecentlyViewedProductDto> getRecentlyViewedProductList(RecentlyViewedProductListParam param) throws Exception;

    int deleteRecentlyViewedProduct(AddRecentlyViewedProductParam param) throws Exception;

    int addRecentlyViewedProduct(AddRecentlyViewedProductParam param) throws Exception;
}