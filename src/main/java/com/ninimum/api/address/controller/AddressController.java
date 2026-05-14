package com.ninimum.api.address.controller;

import com.ninimum.api.address.service.IAddressService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.AddressDto;
import com.ninimum.api.param.AddAddressParam;
import com.ninimum.api.param.AddressListParam;
import com.ninimum.api.param.DeleteAddressParam;
import com.ninimum.api.param.UpdateAddressParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Address", description = "User delivery address APIs.")
@RequestMapping(value={"/samokat/api/v1/address"})
public class AddressController extends BaseController {

    private final IAddressService addressService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Address"},
            summary = "1. Address list",
            description = "Returns delivery address list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getAddressList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getAddressList(@RequestBody AddressListParam param) {
        VersionResponseResult result = null;

        try {
            List<AddressDto> addresses = this.addressService.getAddressList(param);
            result = this.setResult(Result.SUCCESS, addresses);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AddressController => getAddressList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Address"},
            summary = "2. Add address",
            description = "Adds a new delivery address.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addAddress", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addAddress(@RequestBody AddAddressParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.addressService.addAddress(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AddressController => addAddress: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Address"},
            summary = "3. Update address",
            description = "Updates delivery address.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/updateAddress", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> updateAddress(@RequestBody UpdateAddressParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.addressService.updateAddress(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AddressController => updateAddress: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Address"},
            summary = "4. Delete address",
            description = "Deletes delivery address.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/deleteAddress", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deleteAddress(@RequestBody DeleteAddressParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.addressService.deleteAddress(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AddressController => deleteAddress: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}