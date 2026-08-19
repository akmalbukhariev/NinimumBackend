package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class PaymeResponse {
    private String jsonrpc = "2.0";
    private Object result;
    private PaymeError error;
    private Object id;

    public static PaymeResponse success(Object result, Object id) {
        PaymeResponse response = new PaymeResponse();
        response.setResult(result);
        response.setId(id);
        return response;
    }

    public static PaymeResponse error(Integer code, String message, Object data, Object id) {
        PaymeResponse response = new PaymeResponse();
        response.setError(new PaymeError(code, message, data));
        response.setId(id);
        return response;
    }
}