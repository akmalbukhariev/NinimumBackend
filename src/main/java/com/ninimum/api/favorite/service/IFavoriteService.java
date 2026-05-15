package com.ninimum.api.favorite.service;

import com.ninimum.api.dto.FavoriteCountDto;
import com.ninimum.api.dto.FavoriteDto;
import com.ninimum.api.param.AddFavoriteParam;
import com.ninimum.api.param.DeleteFavoriteParam;
import com.ninimum.api.param.FavoriteListParam;

import java.util.List;

public interface IFavoriteService {
    List<FavoriteDto> getFavoriteList(FavoriteListParam param) throws Exception;

    int addFavorite(AddFavoriteParam param) throws Exception;

    int deleteFavorite(DeleteFavoriteParam param) throws Exception;
    FavoriteCountDto getFavoriteCount(FavoriteListParam param) throws Exception;
}