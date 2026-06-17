package com.ninimum.api.param;

import lombok.Data;

@Data
public class SetDefaultPaymentCardParam {
    private Long user_id;
    private Long card_id;
}