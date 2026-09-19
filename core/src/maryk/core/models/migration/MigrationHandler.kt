package maryk.core.models.migration

import maryk.core.models.IsRootDataModel

typealias StoredRootDataModelDefinition = IsRootDataModel
typealias NewRootDataModelDefinition = IsRootDataModel

/**
 * Optional pre-migration expansion hook.
 */
typealias MigrationExpandHandler<DS> = suspend (MigrationContext<DS>) -> MigrationOutcome

/**
 * Handles data backfill migration with context and rich outcomes.
 */
typealias MigrationHandler<DS> = suspend (MigrationContext<DS>) -> MigrationOutcome

/**
 * Optional post-migration verification hook.
 */
typealias MigrationVerifyHandler<DS> = suspend (MigrationContext<DS>) -> MigrationOutcome

/**
 * Optional post-verification contraction hook.
 */
typealias MigrationContractHandler<DS> = suspend (MigrationContext<DS>) -> MigrationOutcome
