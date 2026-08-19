package com.ninimum.api.dto.payme.subscribe;

import lombok.Data;

@Data
public class PaymeSubscribeRequest {
    private Integer id;
    private String method;
    private Object params;

    public PaymeSubscribeRequest(Integer id, String method, Object params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }
}