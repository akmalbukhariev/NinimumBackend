package com.ninimum.api.param;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewImageParam {
    private Long review_id;
    private String image_url;
}
