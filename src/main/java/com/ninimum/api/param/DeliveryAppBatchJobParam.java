package com.ninimum.api.param;

import lombok.Data;
import java.util.List;

@Data
public class DeliveryAppBatchJobParam {
    private List<Long> jobIds;

    /** Where the courier's phone was when the button was pressed (V1a delivery proof). */
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Boolean mock;
    /** Why there is no location: PERMISSION_DENIED, GPS_OFF, TIMEOUT. */
    private String locationError;
}
