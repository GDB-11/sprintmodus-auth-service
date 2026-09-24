package com.sprintmodus.auth_service.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.sprintmodus.common_lib.web.CommonWebConfiguration;

@Configuration(proxyBeanMethods = false)
@Import(CommonWebConfiguration.class)
@EnableConfigurationProperties({ JwtProperties.class, TenantCredentialsProperties.class })
class ApplicationConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
