package com.ninimum.api.category.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.param.AddCategoryParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper {
    int insertCategory(AddCategoryParam param) throws Exception;

    List<CamelCaseMap> selectCategories() throws Exception;
}