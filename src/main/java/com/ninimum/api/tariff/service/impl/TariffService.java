package com.ninimum.api.tariff.service.impl;

import com.ninimum.api.dto.TariffDto;
import com.ninimum.api.param.TariffDetailParam;
import com.ninimum.api.tariff.service.ITariffService;
import com.ninimum.api.tariff.service.TariffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TariffService implements ITariffService {

    private final TariffMapper tariffMapper;

    @Override
    public List<TariffDto> getTariffList() throws Exception {
        return this.tariffMapper.getTariffList();
    }

    @Override
    public TariffDto getTariffDetail(TariffDetailParam param) throws Exception {
        return this.tariffMapper.getTariffDetail(param);
    }
}