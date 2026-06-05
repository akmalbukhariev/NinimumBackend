package com.ninimum.api.category.service;

import com.ninimum.api.dto.CategoryDto;
import com.ninimum.api.param.AddCategoryParam;

import java.util.List;

public interface ICategoryService {
    int createCategory(AddCategoryParam param) throws Exception;

    List<CategoryDto> getCategories() throws Exception;
}