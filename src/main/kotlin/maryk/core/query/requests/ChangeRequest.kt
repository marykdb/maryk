package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.changes.DataObjectChange

/** A Request to change DataObjects for specific DataModel
 * @param dataModel Root model of data to change objects in
 * @param objectChanges Array of object changes
 */
data class ChangeRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val objectChanges: List<DataObjectChange<DO>>
) : IsObjectRequest<DO, DM> {
    constructor(dataModel: DM, vararg objectChange: DataObjectChange<DO>) : this(dataModel, objectChange.toList())

    object Properties {
        val objectChanges = ListDefinition(
                name = "objectChanges",
                index = 1,
                required = true,
                valueDefinition = SubModelDefinition(
                        required = true,
                        dataModel = DataObjectChange
                )
        )
    }

    companion object: QueryDataModel<ChangeRequest<*, *>>(
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, ChangeRequest<*, *>::dataModel),
                    Def(Properties.objectChanges, ChangeRequest<*, *>::objectChanges)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ChangeRequest(
                dataModel = map[0] as RootDataModel<Any>,
                objectChanges = map[1] as List<DataObjectChange<Any>>
        )
    }
}
