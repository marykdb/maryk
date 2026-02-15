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
                    isDeleted = false
                )
            )
        }
        is ChangeUpdate<DM> -> {
            if (response.key == previousResults.getOrNull(response.index)?.key) {
                previousResults.mapIndexed { index, value ->
                    when (index) {
                        response.index -> {
                            ValuesWithMetaData(
                                key = response.key,
                                firstVersion = value.firstVersion,
                                lastVersion = response.version,
                                values = value.values.change(response.changes),
                                isDeleted = response.changes.firstOrNull().let { it is ObjectSoftDeleteChange && it.isDeleted }
                            )
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

                    add(response.index, ValuesWithMetaData(
                        key = response.key,
                        firstVersion = value.firstVersion,
                        lastVersion = response.version,
                        values = value.values.change(response.changes),
                        isDeleted = response.changes.firstOrNull().let { it is ObjectSoftDeleteChange && it.isDeleted }
                    ))
                }
            }
        }
        is RemovalUpdate<DM> -> previousResults.filter { it.key != response.key }
        is OrderedKeysUpdate<DM> -> previousResults
        else -> throw IllegalStateException("Unknown update response type: $response")
    }
