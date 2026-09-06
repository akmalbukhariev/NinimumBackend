package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddProductQuestionParam {
    private Long product_id;
    private Long user_id;
    private String question;
}
