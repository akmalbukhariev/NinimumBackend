package com.ninimum.api.tariff.service;

import com.ninimum.api.dto.TariffDto;
import com.ninimum.api.param.TariffDetailParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TariffMapper {
    List<TariffDto> getTariffList() throws Exception;

    TariffDto getTariffDetail(TariffDetailParam param) throws Exception;
}