package com.ninimum.api.favorite.service;

import com.ninimum.api.dto.FavoriteDto;
import com.ninimum.api.param.AddFavoriteParam;
import com.ninimum.api.param.DeleteFavoriteParam;
import com.ninimum.api.param.FavoriteListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FavoriteMapper {
    List<FavoriteDto> getFavoriteList(FavoriteListParam param) throws Exception;

    int addFavorite(AddFavoriteParam param) throws Exception;

    int deleteFavorite(DeleteFavoriteParam param) throws Exception;
}