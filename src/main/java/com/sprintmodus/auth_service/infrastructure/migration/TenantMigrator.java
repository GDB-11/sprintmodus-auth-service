package com.sprintmodus.auth_service.infrastructure.migration;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.sprintmodus.common_lib.tenant.TenantDataSourceProperties;
import com.sprintmodus.common_lib.tenant.TenantDatabaseNameResolver;

/**
 * Brings every active tenant's database up to the schema shipped in the {@code db-tenant} jar, through Flyway (so the schema
 * history stays truthful; scripts are never piped into {@code mysql}). The master database is migrated by
 * {@code MasterPersistenceConfig} when the application starts, so by the time this runs it is current. A tenant that fails
 * does not stop the others: every failure is reported at the end.
 */
@Component
public class TenantMigrator {

	private static final Logger log = LoggerFactory.getLogger(TenantMigrator.class);

	/** {@code migrated} are the databases now at the latest version (including the ones that already were). */
	public record Report(List<String> migrated, List<String> failed) {

		public boolean succeeded() {
			return failed.isEmpty();
		}

	}

	private final JdbcTemplate master;

	private final TenantDataSourceProperties tenantProperties;

	private final TenantDatabaseNameResolver resolver;

	TenantMigrator(@Qualifier("masterDataSource") DataSource masterDataSource, TenantDataSourceProperties tenantProperties,
			TenantDatabaseNameResolver resolver) {
		this.master = new JdbcTemplate(masterDataSource);
		this.tenantProperties = tenantProperties;
		this.resolver = resolver;
	}

	public Report migrateAll() {
		List<String> databases = master.queryForList("SELECT DatabaseName FROM Organization WHERE IsActive = TRUE ORDER BY OrganizationId",
				String.class);
		log.info("Migrating {} active tenant database(s)", databases.size());
		List<String> migrated = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		for (String database : databases) {
			try {
				migrate(database);
				migrated.add(database);
			}
			catch (RuntimeException e) {
				log.error("Could not migrate {}", database, e);
				failed.add(database);
			}
		}
		log.info("Tenant migration finished: {} migrated, {} failed", migrated.size(), failed.size());
		return new Report(migrated, failed);
	}

	private void migrate(String database) {
		// the name goes into a JDBC URL, so it is checked against the exact shape onboarding produces
		if (!resolver.isTenantDatabaseName(database)) {
			throw new IllegalStateException("Unexpected tenant database name " + database);
		}
		Flyway flyway = Flyway.configure()
				.dataSource(tenantProperties.jdbcUrl(database), tenantProperties.username(), tenantProperties.password())
				.locations("classpath:db/migration/tenant")
				.load();
		var result = flyway.migrate();
		var current = flyway.info().current();
		log.info("{}: {} migration(s) applied, now at version {}", database, result.migrationsExecuted,
				current == null ? "none" : current.getVersion());
	}

}
