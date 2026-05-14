package com.ninimum.api.delivery.service;

import com.ninimum.api.dto.DeliveryDto;
import com.ninimum.api.param.DeliveryDetailParam;
import com.ninimum.api.param.DeliveryListParam;
import com.ninimum.api.param.UpdateDeliveryStatusParam;

import java.util.List;

public interface IDeliveryService {
    List<DeliveryDto> getDeliveryList(DeliveryListParam param) throws Exception;

    DeliveryDto getDeliveryDetail(DeliveryDetailParam param) throws Exception;

    int updateDeliveryStatus(UpdateDeliveryStatusParam param) throws Exception;
}