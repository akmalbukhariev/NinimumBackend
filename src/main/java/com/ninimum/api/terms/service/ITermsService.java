package com.ninimum.api.terms.service;

import com.ninimum.api.dto.TermsDto;
import com.ninimum.api.param.TermsDetailParam;

import java.util.List;

public interface ITermsService {
    List<TermsDto> getTermsList() throws Exception;

    TermsDto getTermsDetail(TermsDetailParam param) throws Exception;
}