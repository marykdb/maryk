package maryk.core.models.migration

import maryk.core.models.IsRootDataModel

typealias StoredRootDataModelDefinition = IsRootDataModel
typealias NewRootDataModelDefinition = IsRootDataModel

/**
 * Handles the migration for the to be migrated DataModels.
 * Throws MigrationException if version cannot be handled
 */
typealias MigrationHandler<DS> = suspend (DS, StoredRootDataModelDefinition, NewRootDataModelDefinition) -> Boolean
