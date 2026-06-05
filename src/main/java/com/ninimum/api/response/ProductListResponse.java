package com.ninimum.api.response;

import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.dto.ProductImageDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductListResponse extends ProductDto {
    private boolean liked;
    private List<ProductImageDto> images;
}
