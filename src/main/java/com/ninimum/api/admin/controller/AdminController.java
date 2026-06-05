package com.ninimum.api.admin.controller;

import com.ninimum.api.admin.service.IAdminService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.param.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Address", description = "User delivery address APIs.")
@RequestMapping(value = {"/ninimum/api/v1/admin"})
public class AdminController extends BaseController {

    private final IAdminService adminService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Admin"},
            summary = "1. Register admin",
            description = "Existing admin creates a new admin account.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/register", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> registerAdmin(@RequestBody RegisterAdminParam param) {

        VersionResponseResult result;

        try {
            int resultNum = adminService.registerAdmin(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AdminController => registerAdmin: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Admin"},
            summary = "12. Login",
            description = "The admin will be login.",
            hidden = false,
            responses = {
                    @ApiResponse(responseCode = "200", description = "success")
            }
    )
    @PostMapping(value = "/login", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> login(@RequestBody AdminLoginInfoParam param) {

        VersionResponseResult result = null;

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}