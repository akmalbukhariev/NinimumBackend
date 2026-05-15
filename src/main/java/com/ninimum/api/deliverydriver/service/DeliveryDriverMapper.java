package com.ninimum.api.deliverydriver.service;

import com.ninimum.api.dto.DeliveryDriverDto;
import com.ninimum.api.param.AssignDeliveryDriverParam;
import com.ninimum.api.param.DeliveryDriverDetailParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeliveryDriverMapper {
    List<DeliveryDriverDto> getDeliveryDriverList() throws Exception;

    DeliveryDriverDto getDeliveryDriverDetail(DeliveryDriverDetailParam param) throws Exception;

    int assignDeliveryDriver(AssignDeliveryDriverParam param) throws Exception;
}