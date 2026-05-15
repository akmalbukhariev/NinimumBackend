package com.ninimum.api.deliverydriver.service.impl;

import com.ninimum.api.deliverydriver.service.DeliveryDriverMapper;
import com.ninimum.api.deliverydriver.service.IDeliveryDriverService;
import com.ninimum.api.dto.DeliveryDriverDto;
import com.ninimum.api.param.AssignDeliveryDriverParam;
import com.ninimum.api.param.DeliveryDriverDetailParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryDriverService implements IDeliveryDriverService {

    private final DeliveryDriverMapper deliveryDriverMapper;

    @Override
    public List<DeliveryDriverDto> getDeliveryDriverList() throws Exception {
        return this.deliveryDriverMapper.getDeliveryDriverList();
    }

    @Override
    public DeliveryDriverDto getDeliveryDriverDetail(DeliveryDriverDetailParam param) throws Exception {
        return this.deliveryDriverMapper.getDeliveryDriverDetail(param);
    }

    @Override
    public int assignDeliveryDriver(AssignDeliveryDriverParam param) throws Exception {
        return this.deliveryDriverMapper.assignDeliveryDriver(param);
    }
}