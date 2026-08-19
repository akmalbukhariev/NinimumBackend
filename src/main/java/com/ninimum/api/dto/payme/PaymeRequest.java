package com.ninimum.api.dto.payme;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class PaymeRequest {
    private String jsonrpc;
    private String method;
    private JsonNode params;
    private Object id;
}
