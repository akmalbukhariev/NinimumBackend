package com.ninimum.api.admin.controller;

import com.ninimum.api.admin.service.IAdminService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.param.AdminLoginInfoParam;
import com.ninimum.api.param.RegisterAdminParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Ninimum administration APIs.")
@RequestMapping(value = {"/ninimum/api/v1/admin"})
public class AdminController extends BaseController {

    private final IAdminService adminService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Admin"},
            summary = "Register admin",
            description = "Existing admin creates a new admin account.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/register", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> registerAdmin(@RequestBody RegisterAdminParam param) {
        VersionResponseResult result;

        try {
            int resultNum = adminService.registerAdmin(param);
            result = resultNum != 0 ? this.setResult(Result.SUCCESS) : this.setResult(Result.SERVER_ERROR);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AdminController => registerAdmin: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Admin"},
            summary = "Login",
            description = "Admin login is processed by AdminAuthenticationFilter.",
            responses = { @ApiResponse(responseCode = "200", description = "success") }
    )
    @PostMapping(value = "/login", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> login(@RequestBody AdminLoginInfoParam param) {
        // During normal requests Spring Security's AdminAuthenticationFilter handles this URL first.
        return new ResponseEntity<>(this.setResult(Result.LOGIN_INVALID_TOKEN), HttpStatus.OK);
    }

    @Operation(
            tags = {"Admin"},
            summary = "Current admin",
            description = "Returns the currently authenticated admin profile.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping("/me")
    public ResponseEntity<Object> me(Authentication authentication) {
        VersionResponseResult result;

        try {
            if (authentication == null || authentication.getName() == null) {
                result = this.setResult(Result.LOGIN);
            } else {
                CamelCaseMap profile = adminService.getAdminProfile(authentication.getName());
                result = profile == null ? this.setResult(Result.USER_NOT_EXIST) : this.setResult(Result.SUCCESS, profile);
            }
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AdminController => me: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Admin"},
            summary = "Dashboard",
            description = "Returns live dashboard data for Ninimum Admin.",
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping("/dashboard")
    public ResponseEntity<Object> dashboard() {
        VersionResponseResult result;

        try {
            Map<String, Object> dashboard = adminService.getDashboard();
            result = this.setResult(Result.SUCCESS, dashboard);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AdminController => dashboard: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
