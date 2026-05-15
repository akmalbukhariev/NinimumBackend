package com.ninimum.api.delivery.service;

import com.ninimum.api.dto.DeliveryDto;
import com.ninimum.api.dto.DeliveryTrackingDto;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeliveryMapper {
    List<DeliveryDto> getDeliveryList(DeliveryListParam param) throws Exception;

    DeliveryDto getDeliveryDetail(DeliveryDetailParam param) throws Exception;

    int updateDeliveryStatus(UpdateDeliveryStatusParam param) throws Exception;
    List<DeliveryTrackingDto> getDeliveryTracking(DeliveryTrackingParam param) throws Exception;
    int addDeliveryTracking(AddDeliveryTrackingParam param) throws Exception;
    int getDeliveryCount(DeliveryListParam param) throws Exception;
}