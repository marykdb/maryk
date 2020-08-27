package maryk.datastore.memory.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.updates.Update

internal typealias ChangeStoreAction<DM, P> = StoreAction<DM, P, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias AnyChangeStoreAction = ChangeStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ChangeRequest in a [storeAction] into a data store from [dataStoreFetcher] */
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processChangeRequest(
    version: HLC,
    storeAction: ChangeStoreAction<DM, P>,
    dataStoreFetcher: IsStoreFetcher<*, *>,
    updateSendChannel: SendChannel<Update<*, *>>
) {
    val changeRequest = storeAction.request

    @Suppress("UNCHECKED_CAST")
    val dataStore = dataStoreFetcher(changeRequest.dataModel) as DataStore<DM, P>

    val statuses = mutableListOf<IsChangeResponseStatus<DM>>()

    if (changeRequest.objects.isNotEmpty()) {
        objectChanges@ for (objectChange in changeRequest.objects) {
            val index = dataStore.records.binarySearch { it.key.compareTo(objectChange.key) }
            val status: IsChangeResponseStatus<DM> = if (index >= 0) {
                val objectToChange = dataStore.records[index]

                val lastVersion = objectChange.lastVersion
                // Check if version is within range
                if (lastVersion != null && objectToChange.lastVersion.compareTo(lastVersion) != 0) {
                    statuses.add(
                        ValidationFail(
                            listOf(
                                InvalidValueException(
                                    null,
                                    "Version of object was different than given: ${objectChange.lastVersion} < ${objectToChange.lastVersion}"
                                )
                            )
                        )
                    )
                    continue@objectChanges
                }

                processChange(
                    changeRequest.dataModel,
                    dataStore,
                    objectToChange,
                    objectChange.changes,
                    version,
                    dataStore.keepAllVersions,
                    updateSendChannel
                )
            } else {
                DoesNotExist(objectChange.key)
            }

            statuses.add(status)
        }
    }

    storeAction.response.complete(
        ChangeResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}
