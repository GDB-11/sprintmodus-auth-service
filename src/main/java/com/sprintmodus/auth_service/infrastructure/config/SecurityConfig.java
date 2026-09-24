package com.sprintmodus.auth_service.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security is used here for password hashing and to close everything that is not an auth endpoint. The auth
 * endpoints are public by design: they are how a caller gets a token, and the token endpoints read the bearer token
 * themselves. CORS is not configured, the gateway is the only CORS authority.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/auth/**", "/error").permitAll()
						.anyRequest().denyAll())
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Users are checked by the login use case; this only stops Boot from generating a default user and password. */
	@Bean
	UserDetailsService noUserDetails() {
		return username -> {
			throw new UsernameNotFoundException("Users are authenticated by LoginUseCase");
		};
	}

}
