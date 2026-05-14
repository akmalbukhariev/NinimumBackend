package com.ninimum.api.qr.service;

import com.ninimum.api.dto.QrDto;
import com.ninimum.api.param.CreateQrParam;
import com.ninimum.api.param.QrParam;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QrMapper {
    QrDto getUserQr(QrParam param) throws Exception;

    int createUserQr(CreateQrParam param) throws Exception;
}