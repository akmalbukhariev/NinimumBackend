package com.ninimum.api.response;

import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.dto.ProductImageDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponse extends ProductDto {
    private boolean liked;
    private Double average_rating;
    private Integer review_count;
    private List<ProductImageDto> images;
}
