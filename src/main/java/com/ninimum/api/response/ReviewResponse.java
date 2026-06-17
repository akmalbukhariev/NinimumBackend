package com.ninimum.api.response;

import com.ninimum.api.dto.ReviewDto;
import com.ninimum.api.dto.ReviewImageDto;
import com.ninimum.api.param.ReviewImageParam;
import lombok.Data;

import java.util.List;

@Data
public class ReviewResponse extends ReviewDto {
    private List<ReviewImageDto> images;
}
