package com.ninimum.api.tariff.service;

import com.ninimum.api.dto.TariffDto;
import com.ninimum.api.param.TariffDetailParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TariffMapper {
    java.util.List<java.util.Map<String,Object>> getProductBenefits(@org.apache.ibatis.annotations.Param("tariffId") Long tariffId);
    List<TariffDto> getTariffList() throws Exception;

    TariffDto getTariffDetail(TariffDetailParam param) throws Exception;
}