package com.atakant.emailtracker.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/health")) // tweak as needed
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/health", "/swagger-ui/**", "/v3/api-docs/**", "/verify.html").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/oauth2/authorization/google") // Spring generates this
                        .successHandler(customOAuth2SuccessHandler)
                )
                .logout(Customizer.withDefaults());

        return http.build();
    }
}



