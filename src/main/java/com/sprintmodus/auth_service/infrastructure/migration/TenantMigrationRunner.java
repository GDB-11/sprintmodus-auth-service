package com.sprintmodus.auth_service.infrastructure.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The one-shot {@code migrator} run: {@code SPRING_PROFILES_ACTIVE=migrator} (see {@code application-migrator.properties}).
 * The master database is migrated during startup, then every active tenant, then the process exits with status 0, or 1 if any
 * tenant failed, so a container orchestrator sees the result. It never runs in the normal service.
 */
@Component
@Profile("migrator")
class TenantMigrationRunner implements ApplicationRunner {

	private final TenantMigrator migrator;

	private final ConfigurableApplicationContext context;

	TenantMigrationRunner(TenantMigrator migrator, ConfigurableApplicationContext context) {
		this.migrator = migrator;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		int status = migrator.migrateAll().succeeded() ? 0 : 1;
		System.exit(SpringApplication.exit(context, () -> status));
	}

}
