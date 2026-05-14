package com.ninimum.api.qr.service;

import com.ninimum.api.dto.QrDto;
import com.ninimum.api.param.CreateQrParam;
import com.ninimum.api.param.QrParam;

public interface IQrService {
    QrDto getUserQr(QrParam param) throws Exception;

    int createUserQr(CreateQrParam param) throws Exception;
}