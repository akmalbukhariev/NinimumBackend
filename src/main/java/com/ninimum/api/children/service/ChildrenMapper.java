package com.ninimum.api.children.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.param.AddChildParam;
import com.ninimum.api.param.DeleteChildParam;
import com.ninimum.api.param.UpdateChildParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChildrenMapper {
    List<CamelCaseMap> selectChildrenByUserId(Long user_id) throws Exception;

    int insertChild(AddChildParam param) throws Exception;

    int updateChild(UpdateChildParam param) throws Exception;

    int deleteChild(DeleteChildParam param) throws Exception;
}