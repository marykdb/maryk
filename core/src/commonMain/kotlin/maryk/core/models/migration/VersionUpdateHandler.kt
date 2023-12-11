package maryk.core.models.migration

/**
 * Notifies of update. Useful to then add specific data or log updates.
 */
typealias VersionUpdateHandler<DS> = (DS, StoredRootDataModelDefinition?, NewRootDataModelDefinition) -> Unit
