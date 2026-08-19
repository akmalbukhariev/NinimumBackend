package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class GetPaymeStatementParam {
    private Long from_time;
    private Long to_time;
}