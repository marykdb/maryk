package maryk.datastore.shared.migration

import maryk.core.models.IsRootValuesDataModel

typealias StoredRootDataModel = IsRootValuesDataModel<*>
typealias NewRootDataModel = IsRootValuesDataModel<*>

/**
 * Handles the migration for the to be migrated DataModels.
 * Throws MigrationException if version cannot be handled
 */
typealias MigrationHandler<DS> = (DS, StoredRootDataModel, NewRootDataModel) -> Unit
