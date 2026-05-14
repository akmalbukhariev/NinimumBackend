package com.ninimum.api.address.service.impl;

import com.ninimum.api.address.service.AddressMapper;
import com.ninimum.api.address.service.IAddressService;
import com.ninimum.api.dto.AddressDto;
import com.ninimum.api.param.AddAddressParam;
import com.ninimum.api.param.AddressListParam;
import com.ninimum.api.param.DeleteAddressParam;
import com.ninimum.api.param.UpdateAddressParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService implements IAddressService {

    private final AddressMapper addressMapper;

    @Override
    public List<AddressDto> getAddressList(AddressListParam param) throws Exception {
        return this.addressMapper.getAddressList(param);
    }

    @Override
    @Transactional
    public int addAddress(AddAddressParam param) throws Exception {
        if ("Y".equals(param.getDefaultYn())) {
            this.addressMapper.resetDefaultAddress(param.getUserId());
        }

        return this.addressMapper.addAddress(param);
    }

    @Override
    @Transactional
    public int updateAddress(UpdateAddressParam param) throws Exception {
        if ("Y".equals(param.getDefaultYn())) {
            this.addressMapper.resetDefaultAddress(param.getUserId());
        }

        return this.addressMapper.updateAddress(param);
    }

    @Override
    public int deleteAddress(DeleteAddressParam param) throws Exception {
        return this.addressMapper.deleteAddress(param);
    }
}