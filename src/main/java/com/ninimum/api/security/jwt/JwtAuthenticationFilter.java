package com.ninimum.api.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.constants.UserOrCompanyStatus;
import com.ninimum.api.dto.UserInfoDto;
import com.ninimum.api.security.provider.UserAuthenticationProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter{
	private final JwtTokenProvider jwtTokenProvider;
	private final UserAuthenticationProvider userAuthenticationProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {

		String path = request.getServletPath();
		//log.info("############## {} ###############", path);

		if (path.equals("/ecoplatesuser/api/v1/user/login")) {
			filterChain.doFilter(request, response);
			return;
		}

		// 1. Extract JWT token from Request Header
		String token = resolveToken(request);
		userAuthenticationProvider.headerToken = token;
		//log.info("======== token: {}", token);

		// 2. Validate token with validateToken
		if (token != null) {
			Result result = jwtTokenProvider.validateToken(token);
			if (result == Result.TOKEN_INVALID) {
				sendErrorResponse(response, Result.TOKEN_INVALID);
				return;
			}
			else if(result == Result.TOKEN_EXPIRED_TIME){
				sendErrorResponse(response, Result.TOKEN_EXPIRED_TIME);
				return;
			}

			try {
				Claims claims = jwtTokenProvider.parseClaims(token);
				String roles = claims.get("auth", String.class);
				//log.info("JWT roles => {}", roles);

				// 3) Check company or admin role
				if (roles != null && (roles.contains(Constant.ROLE_COMPANY) || roles.contains(Constant.ROLE_ADMIN))){
					if(roles.contains(Constant.ROLE_COMPANY)) {
						//log.info("Detected 'ROLE_COMPANY' token => user from Company Service");
					}
					else if(roles.contains(Constant.ROLE_ADMIN)){
						//log.info("Detected 'ROLE_ADMIN' token => user from Admin Service");
					}
					Authentication authentication = jwtTokenProvider.getAuthentication(token);
					SecurityContextHolder.getContext().setAuthentication(authentication);

					filterChain.doFilter(request, response);
					return;
				}

				// 4) For user role
				CamelCaseMap found = userAuthenticationProvider.getUserByToken(token);
				UserInfoDto dto = found == null ? null : found.toObject(UserInfoDto.class);

				if(result == Result.SUCCESS){
					if (dto != null && dto.isDeleted()){
						sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, Result.DELETE_USER.getCodeToString(), Result.DELETE_USER.getMessage(), dto);
					}
					else if (dto != null && dto.getStatus() == UserOrCompanyStatus.BANNED){
						sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, Result.BLOCK_USER.getCodeToString(), Result.BLOCK_USER.getMessage(), dto);
					}
					else if (dto != null && dto.getStatus() == UserOrCompanyStatus.INACTIVE) {
						sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, Result.LOGIN_INACTIVE.getCodeToString(), Result.LOGIN_INACTIVE.getMessage(), dto);
					}
					else{
						Authentication authentication = jwtTokenProvider.getAuthentication(token);
						SecurityContextHolder.getContext().setAuthentication(authentication);

						filterChain.doFilter(request, response);
					}
				}
			} catch (Exception ex) {
				log.error("JwtAuthenticationFilter => doFilterInternal", ex);
			}
		} else {
			// Missing token response
			sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, Result.AUTHENTICATION_ERROR.getCodeToString(), Result.AUTHENTICATION_ERROR.getMessage(), null);
		}
	}

	/**
	 * Extracts the JWT token from the Authorization header.
	 */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(Constant.HEADER_AUTH);
        
        log.info("bearerToken==> {}", bearerToken);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(Constant.HEADER_BEARER)) {
            return bearerToken.substring(7);
        }
        return null;
    }

	/**
	 * Sends a JSON error response.
	 */
	private void sendErrorResponse(HttpServletResponse response, int status, String resultCode, String resultMsg, UserInfoDto dto) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");

		VersionResponseResult resResult = new VersionResponseResult();
		resResult.setResultCode(resultCode);
		if (dto != null && dto.getBlocked_until() != null) {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			resResult.setResultMsg(dto.getBlocked_until().format(formatter));
		} else {
			resResult.setResultMsg(resultMsg);
		}

		/*Map<String, String> error = new HashMap<>();
		error.put("resultCode", resultCode);
		error.put("resultMsg", resultMsg);*/

		new ObjectMapper().writeValue(response.getOutputStream(), resResult);
	}

	/**
	 * Send an error response to the client.
	 */
	private void sendErrorResponse(HttpServletResponse response, Result result) {
		try {
			response.setHeader(Constant.HEADER_ACCESS_TOKEN, "");
			response.setHeader(Constant.HEADER_REFRESH_TOKEN, "");
			response.setHeader(Constant.HEADER_ROLE, "");
			response.setHeader(Constant.HEADER_USER_NAME, "");
			response.setContentType("application/json");

			VersionResponseResult resResult = new VersionResponseResult();
			resResult.setResultCode(result.getCodeToString());
			resResult.setResultMsg(result.getMessage());

			new ObjectMapper().writeValue(response.getOutputStream(), resResult);
		} catch (IOException e) {
			log.error("Failed to send error response", e);
		}
	}
}
