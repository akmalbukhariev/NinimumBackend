package com.ninimum.api.terms.service;

import com.ninimum.api.dto.TermsDto;
import com.ninimum.api.param.TermsDetailParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TermsMapper {
    List<TermsDto> getTermsList() throws Exception;

    TermsDto getTermsDetail(TermsDetailParam param) throws Exception;
}