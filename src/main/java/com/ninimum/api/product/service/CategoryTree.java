package com.ninimum.api.product.service;

import com.ninimum.api.dto.ProductCategoryDto;
import java.util.*;

public final class CategoryTree {
    private CategoryTree() { }

    // Only active IDs returned by the catalog are eligible. A visited set also handles bad cycles.
    public static List<Long> descendants(Long root, List<ProductCategoryDto> categories) {
        Map<Long, List<Long>> children = new HashMap<>();
        Set<Long> active = new HashSet<>();
        for (ProductCategoryDto category : categories) {
            active.add(category.getCategoryId());
            children.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category.getCategoryId());
        }
        if (root == null || !active.contains(root)) return Collections.emptyList();
        Set<Long> found = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Long id = pending.removeFirst();
            if (found.add(id)) pending.addAll(children.getOrDefault(id, Collections.emptyList()));
        }
        return new ArrayList<>(found);
    }
}
