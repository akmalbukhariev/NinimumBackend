package com.ninimum.api.message.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.message.service.IMessageService;
import com.ninimum.api.param.ForgotPasswordParam;
import com.ninimum.api.param.VerifyPhoneNumberParam;
import com.ninimum.api.response.VerifyPhoneNumberResponse;
import com.ninimum.api.dto.UserDto;
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
    private final com.ninimum.api.message.SmsCodes smsCodes;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    /** One temporary-password SMS per phone per minute and at most five per day (kept in memory). */
    static final class TempPasswordLimit {
        private final java.util.Map<String, java.util.ArrayDeque<Long>> sent = new java.util.concurrent.ConcurrentHashMap<>();
        boolean tryAcquire(String phone) {
            long now = System.currentTimeMillis();
            var times = sent.computeIfAbsent(phone, k -> new java.util.ArrayDeque<>());
            synchronized (times) {
                while (!times.isEmpty() && now - times.peekFirst() > 86_400_000L) times.pollFirst();
                if (times.size() >= 5 || (!times.isEmpty() && now - times.peekLast() < 60_000L)) return false;
                times.addLast(now);
                return true;
            }
        }
    }
    private final TempPasswordLimit tempPasswordLimit = new TempPasswordLimit();

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
            // Codes for new numbers only (registration, phone change); sign-in codes come from the login itself.
            String phone = com.ninimum.api.common.PhoneNumbers.normalize(param.getPhone_number());
            if (phone == null) {
                result = this.setResult(Result.FAILED);
                result.setResultMsg("Telefon raqami noto‘g‘ri.");
            } else if (this.userService.getUserByPhone(phone) != null) {
                result = this.setResult(Result.USER_EXIST);
            } else {
                this.smsCodes.send(phone, false);
                VerifyPhoneNumberResponse sentTo = new VerifyPhoneNumberResponse();
                sentTo.setPhone_number(param.getPhone_number());
                sentTo.setCode("");
                result = this.setResult(Result.SUCCESS, sentTo);
            }
        } catch (com.ninimum.api.message.SmsCodes.Limited limited) {
            result = this.setResult(Result.FAILED);
            result.setResultCode(limited.getMessage());
            result.setResultMsg(com.ninimum.api.message.SmsCodes.message(limited.getMessage()));
        } catch (Exception var4) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("MessageController => verifyPhoneNumber: ", var4);
        }

        return new ResponseEntity(result, HttpStatus.OK);
    }

    /** The SMS popup checks the typed code here; the code itself never leaves the server. */
    @PostMapping(value = "/checkCode", headers = {"Content-type=application/json"})
    public ResponseEntity<Object> checkCode(@RequestBody java.util.Map<String, Object> body) {
        boolean ok = smsCodes.check(java.util.Objects.toString(body.get("phone_number"), ""), java.util.Objects.toString(body.get("code"), ""));
        VersionResponseResult result = this.setResult(ok ? Result.SUCCESS : Result.FAILED);
        if (!ok) { result.setResultCode("SMS_CODE_INVALID"); result.setResultMsg(com.ninimum.api.message.SmsCodes.message("SMS_CODE_INVALID")); }
        return new ResponseEntity<>(result, HttpStatus.OK);
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
            if (param == null || param.getPhone_number() == null || param.getPhone_number().trim().isEmpty()) {
                result = this.setResult(Result.SEND_TEMP_PASSWORD_FAILED);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            String phoneNumber = param.getPhone_number().trim();
            if (!tempPasswordLimit.tryAcquire(com.ninimum.api.common.PhoneNumbers.sms(phoneNumber))) {
                result = this.setResult(Result.SEND_TEMP_PASSWORD_FAILED);
                result.setResultMsg("Juda ko‘p urinish. Birozdan keyin qayta urinib ko‘ring.");
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            UserDto user = userService.getUserByPhone(phoneNumber);

            if (user == null) {
                result = this.setResult(Result.USER_NOT_EXIST);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            String tempPassword = service.sendTemporaryPassword(com.ninimum.api.common.PhoneNumbers.sms(user.getPhone_number()));

            if (tempPassword == null) {
                result = this.setResult(Result.SEND_TEMP_PASSWORD_FAILED);
            } else {
                ForgotPasswordParam forgotParam = new ForgotPasswordParam();
                // The account's stored number, which may differ from the form the customer typed (M11).
                forgotParam.setPhoneNumber(user.getPhone_number());
                forgotParam.setTempPassword(tempPassword);

                int updated = userService.forgotPassword(forgotParam);

                result = updated == 1
                        ? this.setResult(Result.SUCCESS)
                        : this.setResult(Result.SEND_TEMP_PASSWORD_FAILED);
            }

        } catch (Exception e) {

            result = this.setResult(Result.SERVER_ERROR);
            log.error("MessageController => sendTemporaryPassword: ", e);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
