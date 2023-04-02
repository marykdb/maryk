package maryk.core.models.migration

import maryk.core.models.definitions.IsRootDataModelDefinition

typealias StoredRootDataModelDefinition = IsRootDataModelDefinition<*>
typealias NewRootDataModelDefinition = IsRootDataModelDefinition<*>

/**
 * Handles the migration for the to be migrated DataModels.
 * Throws MigrationException if version cannot be handled
 */
typealias MigrationHandler<DS> = (DS, StoredRootDataModelDefinition, NewRootDataModelDefinition) -> Boolean
