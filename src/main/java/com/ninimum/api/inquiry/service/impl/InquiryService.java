package com.ninimum.api.inquiry.service;

import com.ninimum.api.dto.InquiryDto;
import com.ninimum.api.param.AddInquiryParam;
import com.ninimum.api.param.InquiryDetailParam;
import com.ninimum.api.param.InquiryListParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InquiryService implements IInquiryService {

    private final InquiryMapper inquiryMapper;

    @Override
    public List<InquiryDto> getInquiryList(InquiryListParam param) throws Exception {
        return this.inquiryMapper.getInquiryList(param);
    }

    @Override
    public InquiryDto getInquiryDetail(InquiryDetailParam param) throws Exception {
        return this.inquiryMapper.getInquiryDetail(param);
    }

    @Override
    public int addInquiry(AddInquiryParam param) throws Exception {
        return this.inquiryMapper.addInquiry(param);
    }
}