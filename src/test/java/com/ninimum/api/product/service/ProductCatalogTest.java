package com.ninimum.api.product.service;

import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.product.service.impl.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCatalogTest {
    @Test void categoryImageUrlsAcceptRelativeAbsoluteAndMissingValues() throws Exception {
        ProductMapper mapper = mock(ProductMapper.class);
        ProductService service = new ProductService(mapper, mock(FileService.class), mock(com.ninimum.api.warehouse.WarehouseService.class));
        ReflectionTestUtils.setField(service, "fileAccessUrl", "https://example.test/uploads/");
        ProductCategoryDto relative = new ProductCategoryDto(); relative.setCategoryImageUrl("/categories/a.png");
        ProductCategoryDto absolute = new ProductCategoryDto(); absolute.setCategoryImageUrl("https://cdn.test/b.png");
        ProductCategoryDto missing = new ProductCategoryDto();
        when(mapper.getProductCategoryList()).thenReturn(Arrays.asList(relative, absolute, missing));
        service.getProductCategoryList();
        assertEquals("https://example.test/uploads/categories/a.png", relative.getCategoryImageUrl());
        assertEquals("https://cdn.test/b.png", absolute.getCategoryImageUrl());
        assertNull(missing.getCategoryImageUrl());
    }
}
