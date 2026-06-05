package com.ninimum.api.security;

import com.ninimum.api.admin.service.AdminMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.constants.Constant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDetailsServiceImpl implements UserDetailsService {

    private final AdminMapper adminMapper;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {

        CamelCaseMap admin = adminMapper.getAdminByLoginId(loginId);

        if (admin == null) {
            throw new UsernameNotFoundException("Admin not found");
        }

        return createUserDetails(admin);
    }

    private UserDetails createUserDetails(CamelCaseMap map) {

        UserDetails user = org.springframework.security.core.userdetails.User.builder()
                .username((String) map.get("login_id"))
                .password((String) map.get("password"))
                .authorities(new SimpleGrantedAuthority(Constant.ROLE_ADMIN))
                .build();

        return new CommUserDetails(user, map);
    }

    public CamelCaseMap getAdminByLoginId(String loginId) throws Exception {
        return adminMapper.getAdminByLoginId(loginId);
    }
}