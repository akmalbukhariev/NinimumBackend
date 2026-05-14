package com.ninimum.api.address.service;

import com.ninimum.api.dto.AddressDto;
import com.ninimum.api.param.AddAddressParam;
import com.ninimum.api.param.AddressListParam;
import com.ninimum.api.param.DeleteAddressParam;
import com.ninimum.api.param.UpdateAddressParam;

import java.util.List;

public interface IAddressService {
    List<AddressDto> getAddressList(AddressListParam param) throws Exception;

    int addAddress(AddAddressParam param) throws Exception;

    int updateAddress(UpdateAddressParam param) throws Exception;

    int deleteAddress(DeleteAddressParam param) throws Exception;
}