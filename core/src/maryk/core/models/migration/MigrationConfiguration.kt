package maryk.core.models.migration

data class MigrationConfiguration<TStore>(
    val migrationExpandHandler: MigrationExpandHandler<TStore>? = null,
    val migrationHandler: MigrationHandler<TStore>? = null,
    val migrationVerifyHandler: MigrationVerifyHandler<TStore>? = null,
    val migrationContractHandler: MigrationContractHandler<TStore>? = null,
    val migrationRetryPolicy: MigrationRetryPolicy = MigrationRetryPolicy(),
    val migrationStartupBudgetMs: Long? = null,
    val continueMigrationsInBackground: Boolean = false,
    val migrationLease: MigrationLease? = null,
    val persistMigrationAuditEvents: Boolean = false,
    val migrationAuditLogMaxEntries: Int = 1000,
    val migrationAuditEventReporter: ((MigrationAuditEvent) -> Unit) = ::defaultMigrationAuditEventReporter,
)
