package com.ninimum.api.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.constants.UserStatus;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.security.provider.AdminAuthenticationProvider;
import com.ninimum.api.security.provider.UserAuthenticationProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
	private final AdminAuthenticationProvider adminAuthenticationProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {

		String path = request.getServletPath();
		//log.info("############## {} ###############", path);

		if (path.equals("/ninimum/api/v1/user/login") || path.equals("/ninimum/api/v1/admin/login")) {
			filterChain.doFilter(request, response);
			return;
		}

		// 1. Extract JWT token from Request Header
		String token = resolveToken(request);
		//userAuthenticationProvider.headerToken = token;
		//log.info("======== token: {}", token);

		// 2. Validate token with validateToken
		if (token != null) {
			Result result = jwtTokenProvider.validateToken(token);
			//Claims map = jwtTokenProvider.parseClaims(token);
			//String phone_number = (String) map.get("sub");

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

				String role = claims.get("auth", String.class);
				String loginId = claims.get("sub", String.class);

				if (Constant.ROLE_ADMIN.equals(role)) {
					Authentication authentication = jwtTokenProvider.getAuthentication(token);

					SecurityContextHolder.getContext().setAuthentication(authentication);
					filterChain.doFilter(request, response);
					return;
				}

				// 3) For user role
				CamelCaseMap found = userAuthenticationProvider.getUserByPhoneNumber(loginId);
				UserDto dto = found == null ? null : found.toObject(UserDto.class);

				if(result == Result.SUCCESS){
					if (dto != null && dto.getStatus() == UserStatus.DELETED){
						sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, Result.DELETE_USER.getCodeToString(), Result.DELETE_USER.getMessage(), dto);
					}
					else if (dto != null && dto.getStatus() == UserStatus.BANNED){
						sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, Result.BLOCK_USER.getCodeToString(), Result.BLOCK_USER.getMessage(), dto);
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
			// No JWT: continue the chain. Public endpoints configured with permitAll()
			// can be used by guests; protected endpoints are still rejected by
			// Spring Security because no Authentication is present.
			filterChain.doFilter(request, response);
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
	private void sendErrorResponse(HttpServletResponse response, int status, String resultCode, String resultMsg, UserDto dto) throws IOException {
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
