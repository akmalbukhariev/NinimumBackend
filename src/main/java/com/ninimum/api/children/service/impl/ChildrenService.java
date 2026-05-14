package com.ninimum.api.children.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.ChildrenDto;
import com.ninimum.api.children.service.ChildrenMapper;
import com.ninimum.api.children.service.IChildrenService;
import com.ninimum.api.param.AddChildParam;
import com.ninimum.api.param.DeleteChildParam;
import com.ninimum.api.param.UpdateChildParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChildrenService implements IChildrenService {

    private final ChildrenMapper mapper;

    @Override
    public List<ChildrenDto> getChildrenByUserId(Long user_id) throws Exception {
        List<CamelCaseMap> result = mapper.selectChildrenByUserId(user_id);

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .map(map -> map.toObject(ChildrenDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public int addChild(AddChildParam param) throws Exception {
        return mapper.insertChild(param);
    }

    @Override
    public int updateChild(UpdateChildParam param) throws Exception {
        return mapper.updateChild(param);
    }

    @Override
    public int deleteChild(DeleteChildParam param) throws Exception {
        return mapper.deleteChild(param);
    }
}