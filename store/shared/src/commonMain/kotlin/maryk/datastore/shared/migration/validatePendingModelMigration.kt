package maryk.datastore.shared.migration

import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.MigrationStatus

/**
 * Validate persisted application progress before any opening hook can use the store.
 * A schema comparison alone cannot tell whether rows have already been rewritten.
 * Returns true only for an obsolete finalization marker whose target is published.
 */
suspend fun validatePendingModelMigration(
    state: MigrationState,
    storedModel: IsRootDataModel?,
    configuredModel: IsRootDataModel,
    storedTargetIdentity: ByteArray?,
): Boolean {
    val name = configuredModel.Meta.name
    val storedVersion = storedModel?.Meta?.version?.toString()
    val configuredVersion = configuredModel.Meta.version.toString()
    val status = storedModel?.let { configuredModel.isMigrationNeeded(it) }
    if (
        storedModel?.Meta?.name == name &&
        storedVersion == state.toVersion &&
        configuredVersion == state.toVersion &&
        state.migrationId == "$name:${state.fromVersion}->${state.toVersion}" &&
        state.phase == MigrationPhase.Contract &&
        state.status == MigrationStateStatus.Running &&
        state.message == "Migration phases complete; finalization pending" &&
        status == MigrationStatus.UpToDate
    ) {
        if (storedTargetIdentity == null) {
            // A version advance proves old binaries already published this target. A same-version
            // transition has no such proof when its exact target identity was never persisted.
            if (state.fromVersion != state.toVersion) return true
        } else {
            requireMigrationTargetIdentity(storedTargetIdentity, configuredModel)
            if (storedTargetIdentity.contentEquals(migrationTargetIdentity(storedModel))) return true
        }
    }

    if (
        storedModel?.Meta?.name != name ||
        storedVersion != state.fromVersion ||
        configuredVersion != state.toVersion ||
        state.migrationId != "$name:$storedVersion->$configuredVersion" ||
        status !is MigrationStatus.NeedsMigration
    ) {
        throw MigrationException(
            "Model $name has pending migration ${state.migrationId}; " +
                "reopen with its target schema to resume before reading or writing"
        )
    }
    requireMigrationTargetIdentity(storedTargetIdentity, configuredModel)
    return false
}
