package com.sprintmodus.auth_service.infrastructure.persistence;

import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import com.sprintmodus.auth_service.application.port.external.TenantProvisioner;
import com.sprintmodus.auth_service.infrastructure.config.TenantCredentialsProperties;
import com.sprintmodus.common_lib.tenant.TenantDataSourceProperties;
import com.sprintmodus.common_lib.tenant.TenantDatabaseNameResolver;

/**
 * Creates a tenant's MySQL database and applies the tenant schema (the Flyway scripts shipped in the {@code db-tenant}
 * jar). The {@code CREATE DATABASE} statement runs over the master connection, so that user needs the privilege to
 * create {@code tenant_*} databases.
 */
@Component
class MySqlTenantProvisioner implements TenantProvisioner {

	private static final Logger log = LoggerFactory.getLogger(MySqlTenantProvisioner.class);

	private final JdbcTemplate master;

	private final TenantDataSourceProperties tenantProperties;

	private final TenantDatabaseNameResolver resolver;

	private final TextEncryptor encryptor;

	MySqlTenantProvisioner(@Qualifier("masterDataSource") DataSource masterDataSource,
			TenantDataSourceProperties tenantProperties, TenantDatabaseNameResolver resolver,
			TenantCredentialsProperties credentials) {
		this.master = new JdbcTemplate(masterDataSource);
		this.tenantProperties = tenantProperties;
		this.resolver = resolver;
		this.encryptor = Encryptors.delux(credentials.encryptionPassword(), credentials.salt());
	}

	@Override
	public TenantDatabase create(UUID tenantId) {
		String name = databaseName(tenantId);
		try {
			master.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
			Flyway.configure()
					.dataSource(tenantProperties.jdbcUrl(name), tenantProperties.username(), tenantProperties.password())
					.locations("classpath:db/migration/tenant")
					.load()
					.migrate();
		}
		catch (RuntimeException e) {
			dropQuietly(name);
			throw new TenantProvisioningException("Could not provision tenant database " + name, e);
		}
		log.info("Provisioned tenant database {}", name);
		return new TenantDatabase(tenantProperties.host() + ":" + tenantProperties.port(), name,
				tenantProperties.username(), encryptor.encrypt(tenantProperties.password()));
	}

	@Override
	public void drop(UUID tenantId) {
		master.execute("DROP DATABASE IF EXISTS `" + databaseName(tenantId) + "`");
		log.info("Dropped tenant database {}", databaseName(tenantId));
	}

	private void dropQuietly(String name) {
		try {
			master.execute("DROP DATABASE IF EXISTS `" + name + "`");
		}
		catch (RuntimeException e) {
			log.error("Could not drop partially provisioned database {}; remove it manually", name, e);
		}
	}

	/** The name goes into SQL text, so it is checked against the exact shape the resolver produces. */
	private String databaseName(UUID tenantId) {
		String name = resolver.resolve(tenantId);
		if (!resolver.isTenantDatabaseName(name)) {
			throw new IllegalStateException("Unexpected tenant database name " + name);
		}
		return name;
	}

}
