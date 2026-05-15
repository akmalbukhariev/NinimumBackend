package com.ninimum.api.promotion.service;

import com.ninimum.api.dto.PromotionDto;
import com.ninimum.api.param.PromotionDetailParam;

import java.util.List;

public interface IPromotionService {
    List<PromotionDto> getPromotionList() throws Exception;

    PromotionDto getPromotionDetail(PromotionDetailParam param) throws Exception;
}