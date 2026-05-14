package com.ninimum.api.delivery.service.impl;

import com.ninimum.api.delivery.service.DeliveryMapper;
import com.ninimum.api.delivery.service.IDeliveryService;
import com.ninimum.api.dto.DeliveryDto;
import com.ninimum.api.param.DeliveryDetailParam;
import com.ninimum.api.param.DeliveryListParam;
import com.ninimum.api.param.UpdateDeliveryStatusParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryService implements IDeliveryService {

    private final DeliveryMapper deliveryMapper;

    @Override
    public List<DeliveryDto> getDeliveryList(DeliveryListParam param) throws Exception {
        return this.deliveryMapper.getDeliveryList(param);
    }

    @Override
    public DeliveryDto getDeliveryDetail(DeliveryDetailParam param) throws Exception {
        return this.deliveryMapper.getDeliveryDetail(param);
    }

    @Override
    public int updateDeliveryStatus(UpdateDeliveryStatusParam param) throws Exception {
        return this.deliveryMapper.updateDeliveryStatus(param);
    }
}