package com.ninimum.api.security.provider;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Result;
import com.ninimum.api.security.AdminDetailsServiceImpl;
import com.ninimum.api.security.CommUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthenticationProvider implements AuthenticationProvider {

    private final AdminDetailsServiceImpl adminDetailsService;
    private final PasswordEncoder passwordEncoder;

    public String adminLoginId = "";

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        adminLoginId = authentication.getName();
        String password = (String) authentication.getCredentials();

        CommUserDetails admin;

        try {
            admin = (CommUserDetails) adminDetailsService.loadUserByUsername(adminLoginId);
        } catch (Exception ex) {
            throw new UsernameNotFoundException(Result.USER_NOT_EXIST.getMessage());
        }

        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new BadCredentialsException(Result.PASSWORD_IS_NOT_MATCHED.getMessage());
        }

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        adminLoginId,
                        password,
                        admin.getAuthorities()
                );

        authToken.setDetails(admin.getDataMap());
        return authToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

    public CamelCaseMap getAdminByLoginId(String loginId) throws Exception {
        return adminDetailsService.getAdminByLoginId(loginId);
    }
}