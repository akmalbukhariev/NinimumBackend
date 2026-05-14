package com.ninimum.api.qr.service.impl;

import com.ninimum.api.dto.QrDto;
import com.ninimum.api.param.CreateQrParam;
import com.ninimum.api.param.QrParam;
import com.ninimum.api.qr.service.IQrService;
import com.ninimum.api.qr.service.QrMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QrService implements IQrService {

    private final QrMapper qrMapper;

    @Override
    public QrDto getUserQr(QrParam param) throws Exception {
        return this.qrMapper.getUserQr(param);
    }

    @Override
    public int createUserQr(CreateQrParam param) throws Exception {
        return this.qrMapper.createUserQr(param);
    }
}