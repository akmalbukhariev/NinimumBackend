package com.ninimum.api.notice.service;

import com.ninimum.api.dto.NoticeDto;
import com.ninimum.api.param.NoticeDetailParam;

import java.util.List;

public interface INoticeService {
    List<NoticeDto> getNoticeList() throws Exception;

    NoticeDto getNoticeDetail(NoticeDetailParam param) throws Exception;
}