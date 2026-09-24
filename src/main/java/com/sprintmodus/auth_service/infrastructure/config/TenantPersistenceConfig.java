package com.sprintmodus.auth_service.infrastructure.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.sprintmodus.common_lib.tenant.TenantDataSourceConfiguration;

import jakarta.persistence.EntityManagerFactory;

/**
 * Tenant databases, reached through the routing DataSource from common-lib. There is no database to inspect when the
 * application starts (the tenant is only known per request), so Hibernate is told the dialect instead of asking the
 * connection for it. The native-query interfaces and entities are in {@code infrastructure.persistence.tenant}.
 */
@Configuration(proxyBeanMethods = false)
@Import(TenantDataSourceConfiguration.class)
@EnableJpaRepositories(basePackages = TenantPersistenceConfig.PACKAGE, entityManagerFactoryRef = "tenantEntityManagerFactory",
		transactionManagerRef = "tenantTransactionManager")
class TenantPersistenceConfig {

	static final String PACKAGE = "com.sprintmodus.auth_service.infrastructure.persistence.tenant";

	@Bean
	LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(@Qualifier("tenantDataSource") DataSource tenantDataSource) {
		LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(tenantDataSource);
		factory.setPackagesToScan(PACKAGE);
		factory.setPersistenceUnitName("tenant");
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		factory.setJpaPropertyMap(Map.of(
				"hibernate.dialect", "org.hibernate.dialect.MySQLDialect",
				"hibernate.boot.allow_jdbc_metadata_access", "false",
				"hibernate.jdbc.time_zone", "UTC"));
		return factory;
	}

	@Bean
	PlatformTransactionManager tenantTransactionManager(
			@Qualifier("tenantEntityManagerFactory") EntityManagerFactory tenantEntityManagerFactory) {
		return new JpaTransactionManager(tenantEntityManagerFactory);
	}

}
