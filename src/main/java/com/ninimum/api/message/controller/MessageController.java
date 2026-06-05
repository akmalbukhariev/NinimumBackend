package com.ninimum.api.message.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.message.service.IMessageService;
import com.ninimum.api.param.ForgotPasswordParam;
import com.ninimum.api.param.VerifyPhoneNumberParam;
import com.ninimum.api.response.VerifyPhoneNumberResponse;
import com.ninimum.api.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Message", description = "Handles phone number verification for app users.")
@RequestMapping(value={"/ninimum/api/v1/message"})
public class MessageController extends BaseController {

    private final IMessageService service;
    private final IUserService userService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Message"},
            summary = "1. Send a message",
            description = "Verify the user phone number.",
            hidden = false,
            responses = {@ApiResponse(responseCode = "200", description = "success")})
    @PostMapping(value = "/verifyPhoneNumber", headers = {"Content-type=application/json"})
    public ResponseEntity<Object> verifyPhoneNumber(@RequestBody VerifyPhoneNumberParam param) {
        VersionResponseResult result = null;

        try {
            VerifyPhoneNumberResponse verifyData = this.service.verifyPhoneNumber(param);
            if (verifyData.getCode() == Constant.NO_CODE) {
                result = this.setResult(Result.VERIFY_PHONE_NUMBER_FAILED);
            } else {
                result = this.setResult(Result.SUCCESS, verifyData);
            }
        } catch (Exception var4) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("MessageController => verifyPhoneNumber: ", var4);
        }

        return new ResponseEntity(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Message"},
            summary = "2. Send temporary password",
            description = "Send temporary password to registered phone number.",
            hidden = false,
            responses = {@ApiResponse(responseCode = "200", description = "success")})
    @PostMapping(value = "/sendTemporaryPassword", headers = {"Content-type=application/json"})
    public ResponseEntity<Object> sendTemporaryPassword(@RequestBody VerifyPhoneNumberParam param) {

        VersionResponseResult result;

        try {

            String tempPassword = service.sendTemporaryPassword(param.getPhone_number());

            if (tempPassword == null) {
                result = this.setResult(Result.SEND_TEMP_PASSWORD_FAILED);
            } else {

                ForgotPasswordParam forgotParam = new ForgotPasswordParam();
                forgotParam.setPhoneNumber(param.getPhone_number());
                forgotParam.setTempPassword(tempPassword);

                userService.forgotPassword(forgotParam);

                result = this.setResult(Result.SUCCESS);
            }

        } catch (Exception e) {

            result = this.setResult(Result.SERVER_ERROR);
            log.error("MessageController => sendTemporaryPassword: ", e);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
