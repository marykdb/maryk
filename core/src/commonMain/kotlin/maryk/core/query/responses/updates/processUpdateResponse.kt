package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectSoftDeleteChange

@OptIn(ExperimentalStdlibApi::class)
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processUpdateResponse(response: IsUpdateResponse<DM, P>, previousResults: List<ValuesWithMetaData<DM, P>>) =
    when (response) {
        is AdditionUpdate<DM, P> -> buildList(previousResults.size + 1) {
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
        is ChangeUpdate<DM, P> -> {
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
                        ?: throw Exception("Could not find changed value in previous results: $response")

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
        is RemovalUpdate<DM, P> -> previousResults.filter { it.key != response.key }
        is OrderedKeysUpdate<DM, P> -> previousResults
        else -> throw Exception("Unknown update response type: $response")
    }
