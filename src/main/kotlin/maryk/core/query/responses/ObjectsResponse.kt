package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.DataObjectWithMetaData

/** Response to an objects (Get/Scan) request
 * @param dataModel from which response data was retrieved
 * @param objects found which match the request
 */
data class ObjectsResponse<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val objects: List<DataObjectWithMetaData<DO>>
) : IsDataModelResponse<DO, DM> {
    object Properties {
        val objects = ListDefinition(
                name = "objects",
                index = 1,
                required = true,
                valueDefinition = SubModelDefinition(
                        required = true,
                        dataModel = DataObjectWithMetaData
                )
        )
    }

    companion object: QueryDataModel<ObjectsResponse<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ObjectsResponse(
                        dataModel = it[0] as RootDataModel<Any>,
                        objects = it[1] as List<DataObjectWithMetaData<Any>>
                )
            },
            definitions = listOf(
                    Def(IsDataModelResponse.Properties.dataModel, ObjectsResponse<*, *>::dataModel),
                    Def(Properties.objects, ObjectsResponse<*, *>::objects)
            )
    )
}
