package com.ninimum.api.security.provider;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.constants.UserOrCompanyStatus;
import com.ninimum.api.dto.UserDto;
import com.ninimum.api.security.CommUserDetails;
import com.ninimum.api.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthenticationProvider implements AuthenticationProvider{
	
	private final UserDetailsServiceImpl userDetailsService;
	//private final PasswordEncoder psssEncoder;

	public String headerToken = "";
	public String userId = "";
	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException, UsernameNotFoundException {

		userId = authentication.getName();
		//String passwd 	= (String)authentication.getCredentials();
		
		//log.info("userId => {}", userId);
		//log.info("passwd => {}", passwd);

		CommUserDetails user = null;
		try {
			user = (CommUserDetails) userDetailsService.loadUserByUsername(userId);
		} catch (Exception ex) {
			throw new UsernameNotFoundException(Result.USER_NOT_EXIST.getMessage());
		}

		CamelCaseMap map = null;

		try {
			if (user == null || user.getDataMap() == null) {
				throw new UsernameNotFoundException(Result.USER_NOT_EXIST.getMessage());
			}

			map = (CamelCaseMap) user.getDataMap();
			UserDto dto = map.toObject(UserDto.class);

			/*CircleInfoDto cDto = userDetailsService.getCircleInfo();
			if(cDto != null){
				//dto.setMax_radius_km(cDto.getMax_radius_km());
				map.put("max_radius_km", cDto.getMax_radius_km());
			}

			if (dto != null && dto.getStatus() == UserOrCompanyStatus.BANNED) {
				throw new UsernameNotFoundException(Result.LOGIN_BANNED.getMessage());
			}*/
		}
		catch (UsernameNotFoundException ex) {
			// Re-throw specific exceptions
			throw ex;
		}
		catch (ClassCastException ex) {
			log.error("UserAuthenticationProvider => Error casting data map to CamelCaseMap", ex);
			throw new UsernameNotFoundException(Result.INTERNAL_ERROR.getMessage());
		} catch (Exception ex) {
			log.error("UserAuthenticationProvider => Unexpected error while processing user data", ex);
			throw new UsernameNotFoundException(Result.INTERNAL_ERROR.getMessage());
		}

		/*if(!psssEncoder.matches(passwd, user.getPassword())) {
			throw new BadCredentialsException(Result.PASSWORD_IS_NOT_MATCHED.getMessage());
		}
		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userId, passwd, user.getAuthorities());
		*/

		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userId, null, user.getAuthorities());
		user.getDataMap().remove("password_hash");
		authToken.setDetails(map);//user.getDataMap());
		return authToken;
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}

	public Long getUserSeq(){
		CommUserDetails user = null;
		try{
			user = (CommUserDetails) userDetailsService.loadUserByUsername(userId);
		}catch(Exception e){
			return Long.valueOf(0);
		}

		return (Long) user.getDataMap().get("user_id");
	}

	public UserOrCompanyStatus getUserStatus(Long user_id) throws Exception {
		return userDetailsService.getUserStatus(user_id);
	}

	public int updateUserStatusAndToken(UserDto dto) throws Exception {
		return userDetailsService.updateUserStatusAndToken(dto);
	}

	public CamelCaseMap getUserByToken(String token_mb) throws Exception {
		return userDetailsService.getUserByToken(token_mb);
	}
}
