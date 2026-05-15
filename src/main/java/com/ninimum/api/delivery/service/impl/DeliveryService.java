package com.ninimum.api.delivery.service.impl;

import com.ninimum.api.delivery.service.DeliveryMapper;
import com.ninimum.api.delivery.service.IDeliveryService;
import com.ninimum.api.dto.DeliveryCountDto;
import com.ninimum.api.dto.DeliveryDto;
import com.ninimum.api.dto.DeliveryTrackingDto;
import com.ninimum.api.param.*;
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

    @Override
    public List<DeliveryTrackingDto> getDeliveryTracking(DeliveryTrackingParam param) throws Exception {
        return this.deliveryMapper.getDeliveryTracking(param);
    }

    @Override
    public int addDeliveryTracking(AddDeliveryTrackingParam param) throws Exception {
        return this.deliveryMapper.addDeliveryTracking(param);
    }

    @Override
    public DeliveryCountDto getDeliveryCount(DeliveryListParam param) throws Exception {
        int count = this.deliveryMapper.getDeliveryCount(param);

        DeliveryCountDto dto = new DeliveryCountDto();
        dto.setDeliveryCount(count);

        return dto;
    }
}