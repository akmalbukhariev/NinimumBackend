package com.ninimum.api.favorite.service;

import com.ninimum.api.dto.FavoriteCountDto;
import com.ninimum.api.dto.FavoriteDto;
import com.ninimum.api.param.AddFavoriteParam;
import com.ninimum.api.param.DeleteFavoriteParam;
import com.ninimum.api.param.FavoriteListParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService implements IFavoriteService {

    private final FavoriteMapper favoriteMapper;

    @Override
    public List<FavoriteDto> getFavoriteList(FavoriteListParam param) throws Exception {
        return this.favoriteMapper.getFavoriteList(param);
    }

    @Override
    public int addFavorite(AddFavoriteParam param) throws Exception {
        return this.favoriteMapper.addFavorite(param);
    }

    @Override
    public int deleteFavorite(DeleteFavoriteParam param) throws Exception {
        return this.favoriteMapper.deleteFavorite(param);
    }

    @Override
    public FavoriteCountDto getFavoriteCount(FavoriteListParam param) throws Exception {
        int count = this.favoriteMapper.getFavoriteCount(param);

        FavoriteCountDto dto = new FavoriteCountDto();
        dto.setFavoriteCount(count);

        return dto;
    }
}