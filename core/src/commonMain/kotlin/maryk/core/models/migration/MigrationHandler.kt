package maryk.core.models.migration

import maryk.core.models.IsRootDataModel

typealias StoredRootDataModelDefinition = IsRootDataModel
typealias NewRootDataModelDefinition = IsRootDataModel

/**
 * Handles migration with context and rich outcomes.
 */
typealias MigrationHandler<DS> = suspend (MigrationContext<DS>) -> MigrationOutcome

/**
 * Optional post-migration verification hook.
 */
typealias MigrationVerifyHandler<DS> = suspend (MigrationContext<DS>) -> MigrationOutcome
