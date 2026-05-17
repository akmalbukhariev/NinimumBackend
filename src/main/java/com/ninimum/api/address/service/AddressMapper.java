package com.ninimum.api.address.service;

import com.ninimum.api.dto.AddressDto;
import com.ninimum.api.param.AddAddressParam;
import com.ninimum.api.param.AddressListParam;
import com.ninimum.api.param.DeleteAddressParam;
import com.ninimum.api.param.UpdateAddressParam;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Mapper
public interface AddressMapper {
    List<AddressDto> getAddressList(AddressListParam param) throws Exception;

    int resetDefaultAddress(@Param("userId") Long userId) throws Exception;

    int addAddress(AddAddressParam param) throws Exception;

    int updateAddress(UpdateAddressParam param) throws Exception;

    int deleteAddress(DeleteAddressParam param) throws Exception;
}