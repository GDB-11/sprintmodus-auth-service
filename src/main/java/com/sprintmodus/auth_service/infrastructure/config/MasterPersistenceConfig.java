package com.sprintmodus.auth_service.infrastructure.config;

import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;

/**
 * The master database: organizations, subscriptions and the platform audit log. Its migrations (from the
 * {@code db-master} jar) run at startup, before JPA starts. The native-query interfaces and entities for it are in
 * {@code infrastructure.persistence.master}. These beans are {@code @Primary}, but tenant data is only reached through
 * {@link TenantPersistenceConfig}'s own repositories.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = MasterPersistenceConfig.PACKAGE, entityManagerFactoryRef = "masterEntityManagerFactory",
		transactionManagerRef = "masterTransactionManager")
class MasterPersistenceConfig {

	static final String PACKAGE = "com.sprintmodus.auth_service.infrastructure.persistence.master";

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource.master")
	HikariDataSource masterDataSource() {
		return new HikariDataSource();
	}

	@Bean
	Flyway masterFlyway(@Qualifier("masterDataSource") DataSource masterDataSource) {
		Flyway flyway = Flyway.configure()
				.dataSource(masterDataSource)
				.locations("classpath:db/migration/master")
				.load();
		flyway.migrate();
		return flyway;
	}

	@Bean
	@Primary
	@DependsOn("masterFlyway")
	LocalContainerEntityManagerFactoryBean masterEntityManagerFactory(@Qualifier("masterDataSource") DataSource masterDataSource) {
		LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(masterDataSource);
		factory.setPackagesToScan(PACKAGE);
		factory.setPersistenceUnitName("master");
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		factory.setJpaPropertyMap(Map.of("hibernate.jdbc.time_zone", "UTC"));
		return factory;
	}

	@Bean
	@Primary
	PlatformTransactionManager masterTransactionManager(
			@Qualifier("masterEntityManagerFactory") EntityManagerFactory masterEntityManagerFactory) {
		return new JpaTransactionManager(masterEntityManagerFactory);
	}

}
