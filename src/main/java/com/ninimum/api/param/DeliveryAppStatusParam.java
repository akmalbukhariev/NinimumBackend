package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeliveryAppStatusParam {
    private Long jobId;
    private String status;
    private String note;
    private String photoBase64;
    private java.util.List<ReturnQuantityParam> returnItems;
}
