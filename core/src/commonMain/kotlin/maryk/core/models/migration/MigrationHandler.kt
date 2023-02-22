package maryk.core.models.migration

import maryk.core.models.IsRootDataModel

typealias StoredRootDataModel = IsRootDataModel<*>
typealias NewRootDataModel = IsRootDataModel<*>

/**
 * Handles the migration for the to be migrated DataModels.
 * Throws MigrationException if version cannot be handled
 */
typealias MigrationHandler<DS> = (DS, StoredRootDataModel, NewRootDataModel) -> Boolean
