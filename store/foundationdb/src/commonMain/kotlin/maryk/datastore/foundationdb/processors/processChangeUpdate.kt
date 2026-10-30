package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.fromReplicationChanges
import maryk.datastore.shared.normalizeReplicationChanges

/** Processes a Change UpdateResponse into FoundationDB */
internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processChangeUpdate(
    storeAction: StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>,
) {
    val dataModel = storeAction.request.dataModel
    val update = storeAction.request.update as ChangeUpdate<DM>
    val changes = normalizeReplicationChanges(update.changes)

    val tableDirs = getTableDirs(getDataModelId(dataModel))

    if (changes.contains(ObjectCreate)) {
        val addedValues = dataModel.fromReplicationChanges(null, changes)

        val addStatus = processAdd(
            tableDirs = tableDirs,
            dataModel = dataModel,
            key = update.key,
            version = HLC(update.version),
            objectToAdd = addedValues,
            isDeleted = changes.filterIsInstance<ObjectSoftDeleteChange>().lastOrNull()?.isDeleted ?: false,
            ignoreIfVersionNotNewer = true,
        )

        storeAction.response.complete(
            ProcessResponse(update.version, AddResponse(dataModel, listOf(addStatus)))
        )
    } else {
        val changeStatus = processChange(
            dataModel = dataModel,
            key = update.key,
            lastVersion = null,
            changes = changes,
            version = HLC(update.version),
            tableDirs = tableDirs,
            ignoreIfVersionNotNewer = true,
        )

        storeAction.response.complete(
            ProcessResponse(update.version, ChangeResponse(dataModel, listOf(changeStatus)))
        )
    }
}
