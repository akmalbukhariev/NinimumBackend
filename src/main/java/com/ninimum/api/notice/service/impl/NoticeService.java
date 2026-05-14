package com.ninimum.api.notice.service.impl;

import com.ninimum.api.dto.NoticeDto;
import com.ninimum.api.notice.service.INoticeService;
import com.ninimum.api.notice.service.NoticeMapper;
import com.ninimum.api.param.NoticeDetailParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService implements INoticeService {

    private final NoticeMapper noticeMapper;

    @Override
    public List<NoticeDto> getNoticeList() throws Exception {
        return this.noticeMapper.getNoticeList();
    }

    @Override
    public NoticeDto getNoticeDetail(NoticeDetailParam param) throws Exception {
        return this.noticeMapper.getNoticeDetail(param);
    }
}