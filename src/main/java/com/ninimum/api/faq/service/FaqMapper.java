package com.ninimum.api.faq.service;

import com.ninimum.api.dto.FaqDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FaqMapper {
    List<FaqDto> getFaqList() throws Exception;
}