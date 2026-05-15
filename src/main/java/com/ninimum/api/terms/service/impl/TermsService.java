package com.ninimum.api.terms.service.impl;

import com.ninimum.api.dto.TermsDto;
import com.ninimum.api.param.TermsDetailParam;
import com.ninimum.api.terms.service.ITermsService;
import com.ninimum.api.terms.service.TermsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TermsService implements ITermsService {

    private final TermsMapper termsMapper;

    @Override
    public List<TermsDto> getTermsList() throws Exception {
        return this.termsMapper.getTermsList();
    }

    @Override
    public TermsDto getTermsDetail(TermsDetailParam param) throws Exception {
        return this.termsMapper.getTermsDetail(param);
    }
}