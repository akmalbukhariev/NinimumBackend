package com.ninimum.api.promotion.service.impl;

import com.ninimum.api.dto.PromotionDto;
import com.ninimum.api.param.PromotionDetailParam;
import com.ninimum.api.promotion.service.IPromotionService;
import com.ninimum.api.promotion.service.PromotionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionService implements IPromotionService {

    private final PromotionMapper promotionMapper;

    @Override
    public List<PromotionDto> getPromotionList() throws Exception {
        return this.promotionMapper.getPromotionList();
    }

    @Override
    public PromotionDto getPromotionDetail(PromotionDetailParam param) throws Exception {
        return this.promotionMapper.getPromotionDetail(param);
    }
}