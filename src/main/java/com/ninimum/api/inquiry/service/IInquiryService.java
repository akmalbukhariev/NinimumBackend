package com.ninimum.api.inquiry.service;

import com.ninimum.api.dto.InquiryDto;
import com.ninimum.api.param.AddInquiryParam;
import com.ninimum.api.param.InquiryDetailParam;
import com.ninimum.api.param.InquiryListParam;

import java.util.List;

public interface IInquiryService {
    List<InquiryDto> getInquiryList(InquiryListParam param) throws Exception;

    InquiryDto getInquiryDetail(InquiryDetailParam param) throws Exception;

    int addInquiry(AddInquiryParam param) throws Exception;
}