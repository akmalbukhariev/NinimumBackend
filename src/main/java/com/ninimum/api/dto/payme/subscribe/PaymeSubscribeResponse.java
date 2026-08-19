package com.ninimum.api.dto.payme.subscribe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymeSubscribeResponse<T> {
    private String jsonrpc;
    private T result;
    private PaymeSubscribeError error;
    private Integer id;
}