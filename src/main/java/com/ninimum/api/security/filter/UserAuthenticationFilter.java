package com.ninimum.api.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.constants.UserOrCompanyStatus;
import com.ninimum.api.dto.TokenDto;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.param.UserLoginInfoParam;
import com.ninimum.api.security.jwt.JwtTokenProvider;
import com.ninimum.api.security.provider.UserAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
public class UserAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final UserAuthenticationProvider userAuthenticationProvider;
	private UserLoginInfoParam user = null;

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
		try {
			user = new ObjectMapper().readValue(request.getInputStream(), UserLoginInfoParam.class);
			//log.info("User Info: phone={}, password=****", user.getPhone_number());

			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(user.getPhone_number(), null/*user.getPassword()*/);

			return userAuthenticationProvider.authenticate(authToken);

		} catch (IOException e) {
			log.error("Failed to parse user login information", e);
			sendErrorResponse(response, Result.LOGIN_INVALID_TOKEN);
		}
		return null;
	}

	@Override
	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {

		CamelCaseMap map = (CamelCaseMap) authResult.getDetails();
		Object obj_deleted = map.get("deleted");

		String deleted;
		if (obj_deleted instanceof Integer) {
			deleted = ((Integer) obj_deleted) == 0 ? "false" : "true";
		} else {
			deleted = "false";
		}

		map.put("deleted", deleted);
		//map.put("ROLE", "USER");

		VersionResponseResult resResult = new VersionResponseResult();
		response.setContentType("application/json");

		handleSuccessfulLogin(request, response, authResult, map, resResult);
	}

	@Override
	protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
		log.info("Unsuccessful authentication attempt");

		Result result;

		// Determine the error type
		if (failed instanceof BadCredentialsException && failed.getMessage().equals(Result.PASSWORD_IS_NOT_MATCHED.getMessage())) {
			result = Result.PASSWORD_IS_NOT_MATCHED;
		}
		else if (failed instanceof UsernameNotFoundException && failed.getMessage().equals(Result.USER_NOT_EXIST.getMessage())) {
			result = Result.USER_NOT_EXIST;
		}
		else if (failed instanceof UsernameNotFoundException && failed.getMessage().equals(Result.LOGIN_BANNED.getMessage())){
			result = Result.LOGIN_BANNED;
		}
		else {
			result = Result.LOGIN_INVALID_TOKEN;
		}

		sendErrorResponse(response, result);
	}

	/**
	 * Handle successful login: Generate and store tokens, update user status.
	 */
	private void handleSuccessfulLogin(HttpServletRequest request, HttpServletResponse response, Authentication authResult, CamelCaseMap map, VersionResponseResult resResult) throws IOException {
		// Generate JWT tokens
		TokenDto tokenInfo = jwtTokenProvider.generateToken(authResult);

		if (tokenInfo != null && user != null) {
			try {
				Long userSeq = userAuthenticationProvider.getUserSeq();
				if (!userSeq.equals(0L)) {
					updateUserStatus(userSeq, tokenInfo.getAccessToken());
				}

				map.put("status", "ACTIVE");
				map.put("token_mb", "");

				Object profilePictureUrl = map.get("profile_picture_url");
				if (profilePictureUrl != null) {
					map.put("profile_picture_url", Constant.SERVER_HTTP + Constant.USER_UPLOAD_DIRECTORY_URL + profilePictureUrl.toString());
				}

				//map.put("token_mb", tokenInfo.getAccessToken());

				// Set response headers
				response.setHeader(Constant.HEADER_ACCESS_TOKEN, tokenInfo.getAccessToken());
				response.setHeader(Constant.HEADER_REFRESH_TOKEN, tokenInfo.getRefreshToken());
				response.setHeader(Constant.HEADER_ROLE, Constant.ROLE_USER);
				response.setHeader(Constant.HEADER_USER_NAME, (String) map.get("email"));

				// Set response result
				resResult.setResultCode(Result.SUCCESS.getCodeToString());
				resResult.setResultMsg(Result.SUCCESS.getMessage());
				resResult.setResultData(map);
			} catch (Exception e) {
				log.error("Error updating user status", e);
				sendErrorResponse(response, Result.LOGIN_INVALID_TOKEN);
			}
		} else {
			log.warn("Token generation failed or user info missing");
			sendErrorResponse(response, Result.LOGIN_INVALID_TOKEN);
		}

		new ObjectMapper().writeValue(response.getOutputStream(), resResult);
	}

	/**
	 * Handle duplicate login scenario or empty token.
	 */
	private void handleDuplicateLoginOrEmptyToken(HttpServletResponse response, Result result, VersionResponseResult resResult) throws IOException {
		log.warn("Duplicate login detected");
		response.setHeader(Constant.HEADER_USER_NAME, "");
		response.setHeader(Constant.HEADER_ROLE, "");
		response.setHeader(Constant.HEADER_ACCESS_TOKEN, "");
		response.setHeader(Constant.HEADER_REFRESH_TOKEN, "");

		resResult.setResultCode(result.getCodeToString());
		resResult.setResultMsg(result.getMessage());

		new ObjectMapper().writeValue(response.getOutputStream(), resResult);
	}

	/**
	 * Update user status in the database.
	 */
	private void updateUserStatus(Long userSeq, String accessToken) throws Exception {
		UserDto dto = new UserDto();
		/*dto.setToken_mb(accessToken);
		dto.setUser_id(userSeq);
		dto.setStatus(UserOrCompanyStatus.ACTIVE);*/
		dto.setUpdated_at(LocalDateTime.now());

		userAuthenticationProvider.updateUserStatusAndToken(dto);
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
