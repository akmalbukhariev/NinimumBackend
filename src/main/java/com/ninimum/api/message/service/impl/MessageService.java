package com.ninimum.api.message.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.EskizLoginInfoDto;
import com.ninimum.api.dto.TestPhoneDto;
import com.ninimum.api.message.service.IMessageService;
import com.ninimum.api.message.service.MessageMapper;
import com.ninimum.api.message.service.TestPhoneMapper;
import com.ninimum.api.param.VerifyPhoneNumberParam;
import com.ninimum.api.response.EskizLoginResponse;
import com.ninimum.api.response.EskizMessageRespond;
import com.ninimum.api.response.VerifyPhoneNumberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class MessageService implements IMessageService {
    private final MessageMapper mapper;
    private final TestPhoneMapper testPhoneMapper;

    private final RestTemplate restTemplate;
    private final String BASE_URL = "https://notify.eskiz.uz/api/";
    private final String LOGIN = BASE_URL + "auth/login";
    private final String SEND_MESSAGE = BASE_URL + "message/sms/send";
    private final String REFRESH_TOKEN = BASE_URL + "auth/refresh";
    private static  final String VERIFICATION_TEXT = "SaleTop platformasidan ro'yxatdan o'tish kodi: ";
    private static final String TEMP_PASSWORD_TEXT = "Ninimum uchun vaqtinchalik parolingiz: ";
    @Override
    public VerifyPhoneNumberResponse verifyPhoneNumber(VerifyPhoneNumberParam param) throws Exception {

        VerifyPhoneNumberResponse responseNumber = new VerifyPhoneNumberResponse();
        responseNumber.setPhone_number(param.getPhone_number());
        responseNumber.setCode(Constant.NO_CODE);

        CamelCaseMap camelTestPhone = testPhoneMapper.selectTestPhone(param.getPhone_number());
        //responseNumber.setCode("1111");
        //return responseNumber;

        if (camelTestPhone != null) {
            TestPhoneDto testPhoneDto = camelTestPhone.toObject(TestPhoneDto.class);
            if (Objects.equals(testPhoneDto.getPhone(), param.getPhone_number()) && testPhoneDto.isUse()) {
                responseNumber.setCode(testPhoneDto.getCode());
                return responseNumber; // stop here, don’t send SMS
            }
        }

        CamelCaseMap camelEskiz = mapper.selectUser();
        EskizLoginInfoDto eskizLoginInfoDto = camelEskiz.toObject(EskizLoginInfoDto.class);

        final long LOGIN_AFTER_DAYS = 30; // hard expiry - do fresh login
        final long REFRESH_BEFORE_DAYS = 2; // refresh when within 2 days of expiry
        final long REFRESH_AT_AGE_DAYS = LOGIN_AFTER_DAYS - REFRESH_BEFORE_DAYS; // 28

        LocalDateTime now = LocalDateTime.now();

        if (eskizLoginInfoDto.getToken() == null || eskizLoginInfoDto.getToken().isBlank() || eskizLoginInfoDto.getUpdated_at() == null) {

            EskizLoginResponse loginResponse = login(eskizLoginInfoDto);
            eskizLoginInfoDto.setToken(loginResponse.getData().getToken());

            mapper.updateUserToken(eskizLoginInfoDto);
        } else {
            long ageDays = java.time.temporal.ChronoUnit.DAYS.between(eskizLoginInfoDto.getUpdated_at(), now);

            if (ageDays >= LOGIN_AFTER_DAYS) {
                // 30+ days old - fresh login
                EskizLoginResponse loginResp = login(eskizLoginInfoDto);
                eskizLoginInfoDto.setToken(loginResp.getData().getToken());
                mapper.updateUserToken(eskizLoginInfoDto);

            } else if (ageDays >= REFRESH_AT_AGE_DAYS) {
                // 28 or 29 days old - refresh
                EskizLoginResponse refreshResp = refreshToken(eskizLoginInfoDto.getToken());
                eskizLoginInfoDto.setToken(refreshResp.getData().getToken());
                mapper.updateUserToken(eskizLoginInfoDto);
            }
        }

        String code = String.format("%04d", new Random().nextInt(10000));
        EskizMessageRespond respondMsg = sendMessage(param, eskizLoginInfoDto, code);
        if (Constant.WAITING.equals(respondMsg.getStatus())) {
            responseNumber.setCode(code);
        }

        return responseNumber;
    }

    @Override
    public String sendTemporaryPassword(String phoneNumber) throws Exception {

        String tempPassword = generateTemporaryPassword();

        CamelCaseMap camelEskiz = mapper.selectUser();
        EskizLoginInfoDto eskizLoginInfoDto = camelEskiz.toObject(EskizLoginInfoDto.class);

        final long LOGIN_AFTER_DAYS = 30;
        final long REFRESH_BEFORE_DAYS = 2;
        final long REFRESH_AT_AGE_DAYS = LOGIN_AFTER_DAYS - REFRESH_BEFORE_DAYS;

        LocalDateTime now = LocalDateTime.now();

        if (eskizLoginInfoDto.getToken() == null
                || eskizLoginInfoDto.getToken().isBlank()
                || eskizLoginInfoDto.getUpdated_at() == null) {

            EskizLoginResponse loginResponse = login(eskizLoginInfoDto);
            eskizLoginInfoDto.setToken(loginResponse.getData().getToken());
            mapper.updateUserToken(eskizLoginInfoDto);

        } else {
            long ageDays = java.time.temporal.ChronoUnit.DAYS.between(
                            eskizLoginInfoDto.getUpdated_at(),
                            now);

            if (ageDays >= LOGIN_AFTER_DAYS) {

                EskizLoginResponse loginResp = login(eskizLoginInfoDto);
                eskizLoginInfoDto.setToken(loginResp.getData().getToken());
                mapper.updateUserToken(eskizLoginInfoDto);

            } else if (ageDays >= REFRESH_AT_AGE_DAYS) {

                EskizLoginResponse refreshResp =
                        refreshToken(eskizLoginInfoDto.getToken());

                eskizLoginInfoDto.setToken(refreshResp.getData().getToken());
                mapper.updateUserToken(eskizLoginInfoDto);
            }
        }

        EskizMessageRespond response =
                sendTemporaryPasswordMessage(
                        phoneNumber,
                        eskizLoginInfoDto,
                        tempPassword);

        if (!Constant.WAITING.equals(response.getStatus())) {
            return null;
        }

        return tempPassword;
    }

    private EskizMessageRespond sendMessage(VerifyPhoneNumberParam param, EskizLoginInfoDto dto, String code) throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(dto.getToken());

        /*
        //For the test
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("mobile_phone", param.getPhone_number());
        body.add("message", "Bu Eskiz dan test");
        body.add("from", "4546");
        body.add("callback_url", "http://0000.uz/test.php");
        */

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("mobile_phone", param.getPhone_number());
        body.add("message", VERIFICATION_TEXT + code);
        body.add("from", "4546");
        body.add("callback_url", "http://0000.uz/test.php");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<EskizMessageRespond> response = restTemplate.postForEntity(SEND_MESSAGE, requestEntity, EskizMessageRespond.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        } else {
            throw new RuntimeException("HTTP Request failed with status: " + response.getStatusCode());
        }
    }

    private EskizMessageRespond sendTemporaryPasswordMessage(String phoneNumber,EskizLoginInfoDto dto,String tempPassword) throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(dto.getToken());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        body.add("mobile_phone", phoneNumber);
        body.add("message", TEMP_PASSWORD_TEXT + tempPassword);
        body.add("from", "4546");
        body.add("callback_url", "http://0000.uz/test.php");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<EskizMessageRespond> response =
                restTemplate.postForEntity(
                        SEND_MESSAGE,
                        requestEntity,
                        EskizMessageRespond.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        }

        throw new RuntimeException(
                "HTTP Request failed with status: "
                        + response.getStatusCode());
    }

    private EskizLoginResponse login(EskizLoginInfoDto loginInfo) throws Exception {
        Map<String, String> loginPayload = new HashMap<>();
        loginPayload.put("email", loginInfo.getEmail());
        loginPayload.put("password", loginInfo.getPasword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(loginPayload, headers);

        ResponseEntity<EskizLoginResponse> response = restTemplate.postForEntity(LOGIN, entity, EskizLoginResponse.class);

        if (response.getStatusCode() == HttpStatus.OK &&
                response.getBody() != null &&
                "token_generated".equals(response.getBody().getMessage())) {
            return response.getBody();
        } else {
            throw new Exception("Eskiz login failed");
        }
    }

    private EskizLoginResponse refreshToken(String token) throws Exception{

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<EskizLoginResponse> response = restTemplate.exchange(REFRESH_TOKEN, HttpMethod.PATCH, requestEntity, EskizLoginResponse.class);

        if (response.getStatusCode() == HttpStatus.OK &&
                response.getBody() != null &&
                "token_generated".equals(response.getBody().getMessage())) {
            return response.getBody();
        } else {
            throw new RuntimeException("Refresh token failed with status code: " + response.getStatusCode());
        }
    }

    private String generateTemporaryPassword() {

        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

        Random random = new Random();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    private VersionResponseResult createResult(Result result) {
        VersionResponseResult resResult = new VersionResponseResult();
        resResult.setResultCode(Integer.toString(result.getCode()));
        resResult.setResultMsg(result.getMessage());
        resResult.setApiVersion(Constant.api_version);

        return resResult;
    }
}
