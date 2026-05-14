package com.ninimum.api.tariff.service;

import com.ninimum.api.dto.TariffDto;
import com.ninimum.api.param.TariffDetailParam;

import java.util.List;

public interface ITariffService {
    List<TariffDto> getTariffList() throws Exception;

    TariffDto getTariffDetail(TariffDetailParam param) throws Exception;
}