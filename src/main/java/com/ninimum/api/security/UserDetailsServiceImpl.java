package com.ninimum.api.security;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.constants.UserOrCompanyStatus;
import com.ninimum.api.dto.UserInfoDto;
import com.ninimum.api.user.service.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService{

	private final UserMapper userMapper;
	
	@Override
	public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {

		//log.debug("loadUserByUserPhone===> {} ", userId);
		CamelCaseMap map = null;
		try {
			//map = userMapper.selectUserByPhone(userId);
		} catch (Exception e) {
			throw new UsernameNotFoundException(Result.USER_NOT_EXIST.getMessage());
		}
		
		if(map == null || map.isEmpty()) {
			throw new UsernameNotFoundException(Result.USER_NOT_EXIST.getMessage());
		}
		return createUserDetails(map);
	}

	// If the corresponding User data exists, create a UserDetails object and return it.
    private UserDetails createUserDetails(CamelCaseMap map) {
		UserDetails user = User.builder()
							.username((String)map.get("phone_number"))
							//.password((String)map.get("password_hash"))
							.password("")
							.authorities(new SimpleGrantedAuthority(Constant.ROLE_USER))
							.build();
       
		return new CommUserDetails(user, map);
    }

	public UserOrCompanyStatus getUserStatus(Long user_id) throws Exception {
		return UserOrCompanyStatus.ACTIVE;//userMapper.getUserStatus(user_id);
	}

	public CamelCaseMap getUserByToken(String token_mb) throws Exception {
		//return userMapper.selectUserByToken(token_mb);
		return new CamelCaseMap();
	}

	public int updateUserStatusAndToken(UserInfoDto dto) throws Exception {
		return 1;//userMapper.updateUserStatusAndToken(dto);
	}
}
