package com.ninimum.api.partner.service;

import com.ninimum.api.dto.PartnerDto;
import com.ninimum.api.param.PartnerDetailParam;

import java.util.List;

public interface IPartnerService {
    List<PartnerDto> getPartnerList() throws Exception;

    PartnerDto getPartnerDetail(PartnerDetailParam param) throws Exception;
}