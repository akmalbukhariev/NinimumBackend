package com.ninimum.api.configure;

import com.ninimum.api.security.UserDetailsServiceImpl;
import com.ninimum.api.security.filter.UserAuthenticationFilter;
import com.ninimum.api.security.jwt.JwtAuthenticationFilter;
import com.ninimum.api.security.jwt.JwtTokenProvider;
import com.ninimum.api.security.provider.UserAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    public UserAuthenticationProvider userProvider() {
        return new UserAuthenticationProvider(userDetailsService);
        //return new UserAuthenticationProvider(userDetailsService, passwordEncoder());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors() // Enable CORS
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeHttpRequests()
                .antMatchers(HttpMethod.OPTIONS, "/ninimum/api/v1/**").permitAll()
                .antMatchers("/ninimum/api/v1/**").hasAnyRole("USER");

        /*UserAuthenticationProvider userProvider = userProvider();

        UserAuthenticationFilter userAuthFilter = new UserAuthenticationFilter(jwtTokenProvider, userProvider);
        userAuthFilter.setFilterProcessesUrl("/ninimum/api/v1/user/login");

        http.addFilter(userAuthFilter);
        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userProvider), UsernamePasswordAuthenticationFilter.class);
        */
        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().antMatchers("/**/webjars/**",
                "/v2/api-docs",
                "/swagger-resources",
                "/swagger-resources/**",
                "/configuration/ui",
                "/configuration/security",
                "/swagger-ui/**",
                "/webjars/**",
                "/v3/api-docs/**",

                // Actuator (no context path + with context path)
                "/actuator/health/**", "/actuator/info",
                "/ninimum/actuator/**",

                // Status endpoint (no context path + with context path)
                "/api/v1/status",
                "/ninimum/api/v1/status",

                "/ninimum/api/v1/user/checkUser/**", //it should not be committed
                "/ninimum/api/v1/user/register", //it should not be committed
                "/error");
    }
}