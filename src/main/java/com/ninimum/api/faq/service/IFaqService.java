package com.ninimum.api.faq.service;

import com.ninimum.api.dto.FaqDto;

import java.util.List;

public interface IFaqService {
    List<FaqDto> getFaqList() throws Exception;
}