package com.ninimum.api.configure;

import com.ninimum.api.constants.Constant;
import com.ninimum.api.security.AdminDetailsServiceImpl;
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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.ninimum.api.security.provider.AdminAuthenticationProvider;
import com.ninimum.api.security.filter.AdminAuthenticationFilter;
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final AdminDetailsServiceImpl adminDetailsService;

    public UserAuthenticationProvider userProvider() {
        return new UserAuthenticationProvider(userDetailsService, passwordEncoder());
    }

    public AdminAuthenticationProvider adminProvider() {
        return new AdminAuthenticationProvider(adminDetailsService, passwordEncoder());
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
                .antMatchers("/uploads/**").permitAll()
                .antMatchers("/ninimum/api/v1/admin/login").permitAll()
                .antMatchers("/ninimum/api/v1/user/login").permitAll()
                .antMatchers("/ninimum/api/v1/payment/payme/callback").permitAll()

                // Guest mode: public, read-only storefront APIs used by the MAUI app.
                .antMatchers(HttpMethod.GET,
                        "/ninimum/api/v1/banner/getBannerList",
                        "/ninimum/api/v1/product/getProductCategoryList"
                ).permitAll()
                .antMatchers(HttpMethod.POST,
                        "/ninimum/api/v1/product/getProductList",
                        "/ninimum/api/v1/product/getProductDetail",
                        "/ninimum/api/v1/product/searchProductList",
                        "/ninimum/api/v1/product/getSimilarProductList",
                        "/ninimum/api/v1/review/getReviewList"
                ).permitAll()

                //.antMatchers("/ninimum/api/v1/admin/**").hasAnyAuthority(Constant.ROLE_ADMIN)
                .antMatchers("/ninimum/api/v1/**").hasAnyAuthority(Constant.ROLE_USER, Constant.ROLE_ADMIN);

        UserAuthenticationProvider userProvider = userProvider();
        AdminAuthenticationProvider adminProvider = adminProvider();

        UserAuthenticationFilter userAuthFilter = new UserAuthenticationFilter(jwtTokenProvider, userProvider);
        userAuthFilter.setFilterProcessesUrl("/ninimum/api/v1/user/login");

        AdminAuthenticationFilter adminAuthFilter = new AdminAuthenticationFilter(jwtTokenProvider, adminProvider);
        adminAuthFilter.setFilterProcessesUrl("/ninimum/api/v1/admin/login");

        http.addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, userProvider, adminProvider),
                UsernamePasswordAuthenticationFilter.class
        );

        http.addFilterAt(userAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAt(adminAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().antMatchers("/**/webjars/**",
                "/uploads/**",
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

                "/ninimum/api/v1/user/checkPhoneNumber", //it should not be committed
                "/ninimum/api/v1/user/register",     //it should not be committed
                //"/ninimum/api/v1/admin/register",     //it should not be committed
                "/ninimum/api/v1/message/verifyPhoneNumber",     //it should not be committed
                "/ninimum/api/v1/payment/payme/callback",     //it should not be committed
                "/error");
    }
}