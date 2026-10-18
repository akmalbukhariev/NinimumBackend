package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class CreatePaymeCheckoutUrlParam {
    private Long order_id;
    private Long user_id;
}
