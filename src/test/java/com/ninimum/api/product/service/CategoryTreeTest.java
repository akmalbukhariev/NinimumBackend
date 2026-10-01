package com.ninimum.api.product.service;

import com.ninimum.api.dto.ProductCategoryDto;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CategoryTreeTest {
    private ProductCategoryDto category(long id, Long parent) {
        ProductCategoryDto c = new ProductCategoryDto();
        c.setCategoryId(id); c.setParentId(parent); return c;
    }
    @Test void includesNestedDescendantsButNotOtherBranches() {
        assertEquals(Arrays.asList(1L, 2L, 3L), CategoryTree.descendants(1L,
            Arrays.asList(category(1, null), category(2, 1L), category(3, 2L), category(4, null))));
    }
    @Test void missingOrRemovedCategoryDoesNotReturnAllProducts() {
        assertTrue(CategoryTree.descendants(9L, Arrays.asList(category(2, 9L))).isEmpty());
    }
    @Test void malformedCycleTerminatesWithoutDuplicates() {
        assertEquals(Arrays.asList(1L, 2L), CategoryTree.descendants(1L,
            Arrays.asList(category(1, 2L), category(2, 1L))));
    }
}
