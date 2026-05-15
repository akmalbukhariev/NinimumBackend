package com.ninimum.api.promotion.service;

import com.ninimum.api.dto.PromotionDto;
import com.ninimum.api.param.PromotionDetailParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PromotionMapper {
    List<PromotionDto> getPromotionList() throws Exception;

    PromotionDto getPromotionDetail(PromotionDetailParam param) throws Exception;
}