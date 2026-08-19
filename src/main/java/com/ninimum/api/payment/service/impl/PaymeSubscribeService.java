package com.ninimum.api.payment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.dto.payme.subscribe.*;
import com.ninimum.api.payment.service.IPaymeSubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymeSubscribeService implements IPaymeSubscribeService {

    private final ObjectMapper objectMapper;

    @Value("${payme.subscribe-api-url}")
    private String subscribeApiUrl;

    @Value("${payme.merchant-id}")
    private String merchantId;

    @Value("${payme.key}")
    private String key;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicInteger requestId = new AtomicInteger(1);

    @Override
    public PaymeCardTokenResult createCard(PaymeCreateCardParam param) throws Exception {

        if (param == null || param.getNumber() == null || param.getExpire() == null) {
            throw new Exception("Card number and expire are required");
        }

        Map<String, Object> card = new HashMap<>();
        card.put("number", param.getNumber());
        card.put("expire", param.getExpire());

        Map<String, Object> params = new HashMap<>();
        params.put("card", card);
        params.put("save", true);

        return callPayme(
                "cards.create",
                params,
                merchantId,
                new TypeReference<PaymeSubscribeResponse<PaymeCardTokenResult>>() {}
        ).getResult();
    }

    @Override
    public Boolean getVerifyCode(String token) throws Exception {

        if (token == null || token.trim().isEmpty()) {
            throw new Exception("Token is required");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("token", token);

        PaymeSubscribeResponse<Map<String, Object>> response = callPayme(
                "cards.get_verify_code",
                params,
                merchantId,
                new TypeReference<PaymeSubscribeResponse<Map<String, Object>>>() {}
        );

        return response.getError() == null;
    }

    @Override
    public PaymeCardTokenResult verifyCard(PaymeVerifyCardParam param) throws Exception {

        if (param == null || param.getToken() == null || param.getCode() == null) {
            throw new Exception("Token and code are required");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("token", param.getToken());
        params.put("code", param.getCode());

        return callPayme(
                "cards.verify",
                params,
                merchantId,
                new TypeReference<PaymeSubscribeResponse<PaymeCardTokenResult>>() {}
        ).getResult();
    }

    private <T> T callPayme(String method, Object params, String auth, TypeReference<T> typeReference) throws Exception {

        PaymeSubscribeRequest request = new PaymeSubscribeRequest(
                requestId.getAndIncrement(),
                method,
                params
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Auth", auth);

        HttpEntity<PaymeSubscribeRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                subscribeApiUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new Exception("Payme Subscribe API HTTP error: " + response.getStatusCode());
        }

        T result = objectMapper.readValue(response.getBody(), typeReference);

        PaymeSubscribeResponse<?> paymeResponse = objectMapper.readValue(response.getBody(), new TypeReference<PaymeSubscribeResponse<Object>>() {});

        if (paymeResponse.getError() != null) {
            throw new Exception("Payme error: " + paymeResponse.getError().getMessage());
        }

        return result;
    }
}