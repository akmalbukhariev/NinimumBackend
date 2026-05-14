package com.ninimum.api.faq.service;

import com.ninimum.api.dto.FaqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqService implements IFaqService {

    private final FaqMapper faqMapper;

    @Override
    public List<FaqDto> getFaqList() throws Exception {
        return this.faqMapper.getFaqList();
    }
}