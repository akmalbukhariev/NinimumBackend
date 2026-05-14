package com.ninimum.api.partner.service.impl;

import com.ninimum.api.dto.PartnerDto;
import com.ninimum.api.param.PartnerDetailParam;
import com.ninimum.api.partner.service.IPartnerService;
import com.ninimum.api.partner.service.PartnerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnerService implements IPartnerService {

    private final PartnerMapper partnerMapper;

    @Override
    public List<PartnerDto> getPartnerList() throws Exception {
        return this.partnerMapper.getPartnerList();
    }

    @Override
    public PartnerDto getPartnerDetail(PartnerDetailParam param) throws Exception {
        return this.partnerMapper.getPartnerDetail(param);
    }
}