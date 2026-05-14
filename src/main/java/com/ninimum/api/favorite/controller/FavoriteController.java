package com.ninimum.api.favorite.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.FavoriteDto;
import com.ninimum.api.favorite.service.IFavoriteService;
import com.ninimum.api.param.AddFavoriteParam;
import com.ninimum.api.param.DeleteFavoriteParam;
import com.ninimum.api.param.FavoriteListParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Favorite", description = "Favorite product APIs.")
@RequestMapping(value={"/samokat/api/v1/favorite"})
public class FavoriteController extends BaseController {

    private final IFavoriteService favoriteService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Favorite"},
            summary = "1. Favorite list",
            description = "Returns favorite product list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getFavoriteList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getFavoriteList(@RequestBody FavoriteListParam param) {
        VersionResponseResult result = null;

        try {
            List<FavoriteDto> favorites = this.favoriteService.getFavoriteList(param);
            result = this.setResult(Result.SUCCESS, favorites);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("FavoriteController => getFavoriteList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Favorite"},
            summary = "2. Add favorite",
            description = "Adds product to favorite list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addFavorite", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addFavorite(@RequestBody AddFavoriteParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.favoriteService.addFavorite(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("FavoriteController => addFavorite: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Favorite"},
            summary = "3. Delete favorite",
            description = "Deletes product from favorite list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/deleteFavorite", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deleteFavorite(@RequestBody DeleteFavoriteParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.favoriteService.deleteFavorite(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("FavoriteController => deleteFavorite: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}