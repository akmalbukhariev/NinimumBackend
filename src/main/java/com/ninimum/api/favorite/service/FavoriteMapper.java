package com.ninimum.api.favorite.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.FavoriteDto;
import com.ninimum.api.param.AddFavoriteParam;
import com.ninimum.api.param.DeleteFavoriteParam;
import com.ninimum.api.param.FavoriteListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FavoriteMapper {
    List<CamelCaseMap> getFavoriteList(FavoriteListParam param) throws Exception;

    int addFavorite(AddFavoriteParam param) throws Exception;

    int deleteFavorite(DeleteFavoriteParam param) throws Exception;
    int getFavoriteCount(FavoriteListParam param) throws Exception;
}