package com.ninimum.api.children.service;

import com.ninimum.api.dto.ChildrenDto;
import com.ninimum.api.param.AddChildParam;
import com.ninimum.api.param.DeleteChildParam;
import com.ninimum.api.param.UpdateChildParam;

import java.util.List;

public interface IChildrenService {
    List<ChildrenDto> getChildrenByUserId(Long user_id) throws Exception;
    int addChild(AddChildParam param) throws Exception;

    int updateChild(UpdateChildParam param) throws Exception;

    int deleteChild(DeleteChildParam param) throws Exception;
}