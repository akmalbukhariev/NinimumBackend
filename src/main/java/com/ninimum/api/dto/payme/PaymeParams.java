package com.ninimum.api.dto.payme;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class PaymeParams {
    private Long amount;
    private JsonNode account;

    private String id;
    private Long time;
    private Integer reason;

    private Long from;
    private Long to;

    private String password;
}