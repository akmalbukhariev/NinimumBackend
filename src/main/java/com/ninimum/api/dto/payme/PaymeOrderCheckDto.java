package com.ninimum.api.dto.payme;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymeOrderCheckDto {
    private Long id;
    private BigDecimal total_price;
    private String payment_status;
    private String status;
}