package com.ninimum.api.partner.service;

import com.ninimum.api.dto.PartnerDto;
import com.ninimum.api.param.PartnerDetailParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PartnerMapper {
    List<PartnerDto> getPartnerList() throws Exception;

    PartnerDto getPartnerDetail(PartnerDetailParam param) throws Exception;
}