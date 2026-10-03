package com.ninimum.api.param;

import lombok.Data;
import java.util.List;

@Data
public class DeliveryAppBatchJobParam {
    private List<Long> jobIds;
}
