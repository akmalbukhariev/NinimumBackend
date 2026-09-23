package com.ninimum.api.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.TokenDto;
import com.ninimum.api.param.AdminLoginInfoParam;
import com.ninimum.api.security.jwt.JwtTokenProvider;
import com.ninimum.api.security.provider.AdminAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminAuthenticationProvider adminAuthenticationProvider;

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        try {
            AdminLoginInfoParam admin = new ObjectMapper().readValue(request.getInputStream(), AdminLoginInfoParam.class);
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    admin.getLogin_id(),
                    admin.getPassword()
            );
            return adminAuthenticationProvider.authenticate(authToken);
        } catch (IOException e) {
            log.error("Failed to parse admin login information", e);
            sendErrorResponse(response, Result.LOGIN_INVALID_TOKEN);
        }
        return null;
    }

    @Override
    protected void successfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            Authentication authResult
    ) throws IOException {
        CamelCaseMap adminMap = (CamelCaseMap) authResult.getDetails();
        TokenDto tokenInfo = jwtTokenProvider.generateToken(authResult);

        VersionResponseResult result = new VersionResponseResult();
        result.setApiVersion(Constant.api_version);
        response.setContentType("application/json");

        if (tokenInfo != null) {
            CamelCaseMap responseData = new CamelCaseMap();
            responseData.putAll(adminMap);
            responseData.remove("password");
            responseData.put("access_token", tokenInfo.getAccessToken());
            responseData.put("refresh_token", tokenInfo.getRefreshToken());

            String role = adminMap.get("role") == null ? Constant.ROLE_ADMIN : String.valueOf(adminMap.get("role"));
            response.setHeader(Constant.HEADER_ACCESS_TOKEN, tokenInfo.getAccessToken());
            response.setHeader(Constant.HEADER_REFRESH_TOKEN, tokenInfo.getRefreshToken());
            response.setHeader(Constant.HEADER_ROLE, role);
            response.setHeader(Constant.HEADER_USER_NAME, authResult.getName());

            result.setResultCode(Result.SUCCESS.getCodeToString());
            result.setResultMsg(Result.SUCCESS.getMessage());
            result.setResultData(responseData);
        } else {
            result.setResultCode(Result.LOGIN_INVALID_TOKEN.getCodeToString());
            result.setResultMsg(Result.LOGIN_INVALID_TOKEN.getMessage());
        }

        new ObjectMapper().writeValue(response.getOutputStream(), result);
    }

    @Override
    protected void unsuccessfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failed
    ) throws IOException {
        Result result;

        if (failed instanceof DisabledException) {
            result = Result.LOGIN_INACTIVE;
        } else if (failed instanceof UsernameNotFoundException) {
            result = Result.USER_NOT_EXIST;
        } else if (failed instanceof BadCredentialsException && Result.ROLE_INVALID.getMessage().equals(failed.getMessage())) {
            result = Result.ROLE_INVALID;
        } else if (failed instanceof BadCredentialsException) {
            result = Result.PASSWORD_IS_NOT_MATCHED;
        } else {
            result = Result.LOGIN_INVALID_TOKEN;
        }

        sendErrorResponse(response, result);
    }

    private void sendErrorResponse(HttpServletResponse response, Result result) {
        try {
            response.setHeader(Constant.HEADER_ACCESS_TOKEN, "");
            response.setHeader(Constant.HEADER_REFRESH_TOKEN, "");
            response.setHeader(Constant.HEADER_ROLE, "");
            response.setHeader(Constant.HEADER_USER_NAME, "");
            response.setContentType("application/json");

            VersionResponseResult resResult = new VersionResponseResult();
            resResult.setApiVersion(Constant.api_version);
            resResult.setResultCode(result.getCodeToString());
            resResult.setResultMsg(result.getMessage());

            new ObjectMapper().writeValue(response.getOutputStream(), resResult);
        } catch (IOException e) {
            log.error("Failed to send admin login error response", e);
        }
    }
}
