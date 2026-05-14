package com.ninimum.api.user.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.RegisterUserParam;
import com.ninimum.api.param.UpdateUserInfoParam;
import com.ninimum.api.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "User", description = "Stores information about Samokat users.")
@RequestMapping(value={"/samokat/api/v1/user"})
public class UserController extends BaseController {

    private final IUserService userService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"User"},
            summary = "1. Register",
            description = "The new user will be registered.",
            hidden = false,
            responses = {
                    @ApiResponse(responseCode = "200", description = "success")
            }
    )
    @PostMapping(value = "/register", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> register(@RequestBody RegisterUserParam param) {

        VersionResponseResult result = null;

        try {

            UserDto userInfo = this.userService.getUserByPhone(param.getPhone_number());

            if (userInfo != null) {
                result = this.setResult(Result.USER_EXIST);
            }
            else {

                int resultNum = this.userService.register(param);

                if (resultNum != 0) {
                    result = this.setResult(Result.SUCCESS);
                }
                else {
                    result = this.setResult(Result.SERVER_ERROR);
                }
            }

        }
        catch (Exception ex) {

            result = this.setResult(Result.SERVER_ERROR);

            log.error("UserController => register: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"User"},
            summary = "2. User info",
            description = "The user information will be retrieved using their token. Set the token in the header.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getUserInfo")
    public ResponseEntity<Object> getUserInfo(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        VersionResponseResult result = null;

        try {
            if (userDetails == null) {
                result = this.setResult(Result.AUTHENTICATION_ERROR);
            } else {
                UserDto userInfo = this.userService.getUserByPhone(userDetails.getUsername());

                if (userInfo == null) {
                    result = this.setResult(Result.USER_NOT_EXIST);
                } else {
                    result = this.setResult(Result.USER_EXIST, userInfo);
                }
            }
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("UserController => getUserInfo: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"User"},
            summary = "3. Update user info",
            description = "Updates user profile information.",
            hidden = false,
            responses = {
                    @ApiResponse(responseCode = "200", description = "success")
            },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value= "/updateUserInfo", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> updateUserInfo(@RequestBody UpdateUserInfoParam param) {

        VersionResponseResult result = null;

        try {

            int resultNum = this.userService.updateUserInfo(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            }
            else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        }
        catch (Exception ex) {

            result = this.setResult(Result.SERVER_ERROR);

            log.error("UserController => updateUserInfo: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}