package maryk.core.query.responses.updates

import maryk.core.models.IsRootDataModel
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectSoftDeleteChange

fun <DM: IsRootDataModel> processUpdateResponse(response: IsUpdateResponse<DM>, previousResults: List<ValuesWithMetaData<DM>>) =
    when (response) {
        is InitialValuesUpdate<DM> -> response.values
        is InitialChangesUpdate<DM> -> throw IllegalArgumentException("processUpdateResponse cannot work with Change requests/responses")
        is AdditionUpdate<DM> -> buildList(previousResults.size + 1) {
            addAll(previousResults)
            add(response.insertionIndex,
                ValuesWithMetaData(
                    key = response.key,
                    firstVersion = response.firstVersion,
                    lastVersion = response.version,
                    values = response.values,
                    isDeleted = response.isDeleted
                )
            )
        }
        is ChangeUpdate<DM> -> {
            if (response.key == previousResults.getOrNull(response.index)?.key) {
                previousResults.mapIndexed { index, value ->
                    when (index) {
                        response.index -> {
                            value.withChange(response)
                        }
                        else -> value
                    }
                }
            } else {
                buildList(previousResults.size) {
                    addAll(previousResults)
                    val oldIndex = indexOfFirst { it.key == response.key }

                    val value = getOrNull(oldIndex)
                        ?: throw IllegalStateException("Could not find changed value in previous results: $response")

                    removeAt(oldIndex)

                    add(response.index, value.withChange(response))
                }
            }
        }
        is RemovalUpdate<DM> -> previousResults.filter { it.key != response.key }
        is OrderedKeysUpdate<DM> -> previousResults
        else -> throw IllegalStateException("Unknown update response type: $response")
    }

private fun <DM: IsRootDataModel> ValuesWithMetaData<DM>.withChange(response: ChangeUpdate<DM>) =
    ValuesWithMetaData(
        key = response.key,
        firstVersion = firstVersion,
        lastVersion = response.version,
        values = values.change(response.changes),
        isDeleted = response.changes.filterIsInstance<ObjectSoftDeleteChange>().lastOrNull()?.isDeleted ?: isDeleted
    )
