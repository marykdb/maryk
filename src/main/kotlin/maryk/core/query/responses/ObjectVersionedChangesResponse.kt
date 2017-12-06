package maryk.core.query.responses

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.changes.DataObjectVersionedChange

/** Response with all versioned changes since version in request
 * @param dataModel from which response data was retrieved
 * @param changes which occurred since given start version
 */
data class ObjectVersionedChangesResponse<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val changes: List<DataObjectVersionedChange<DO>>
) : IsDataModelResponse<DO, DM> {
    companion object: QueryDataModel<ObjectVersionedChangesResponse<*, *>>(
            properties = object : PropertyDefinitions<ObjectVersionedChangesResponse<*, *>>() {
                init {
                    IsDataModelResponse.addDataModel(this, ObjectVersionedChangesResponse<*, *>::dataModel)
                    add(1, "changes", ListDefinition(
                            required = true,
                            valueDefinition = SubModelDefinition(
                                    required = true,
                                    dataModel = DataObjectVersionedChange
                            )
                    ), ObjectVersionedChangesResponse<*, *>::changes)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ObjectVersionedChangesResponse(
                dataModel = map[0] as RootDataModel<Any>,
                changes = map[1] as List<DataObjectVersionedChange<Any>>
        )
    }
}
