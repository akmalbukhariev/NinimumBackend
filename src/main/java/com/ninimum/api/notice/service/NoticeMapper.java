package com.ninimum.api.notice.service;

import com.ninimum.api.dto.NoticeDto;
import com.ninimum.api.param.NoticeDetailParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface NoticeMapper {
    List<NoticeDto> getNoticeList() throws Exception;

    NoticeDto getNoticeDetail(NoticeDetailParam param) throws Exception;
}