package com.ninimum.api.dto.payme;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymeError {
    private Integer code;
    /** {"ru","uz","en"} as Payme expects; Payme shows it to the payer. */
    private Object message;
    private Object data;
}