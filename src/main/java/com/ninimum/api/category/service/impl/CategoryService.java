package com.ninimum.api.category.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.category.service.CategoryMapper;
import com.ninimum.api.category.service.ICategoryService;
import com.ninimum.api.dto.CategoryDto;
import com.ninimum.api.param.AddCategoryParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService implements ICategoryService {

    private final CategoryMapper mapper;

    @Override
    public int createCategory(AddCategoryParam param) throws Exception {
        return mapper.insertCategory(param);
    }

    @Override
    public List<CategoryDto> getCategories() throws Exception {
        List<CamelCaseMap> result = mapper.selectCategories();

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .map(map -> map.toObject(CategoryDto.class))
                .collect(Collectors.toList());
    }
}