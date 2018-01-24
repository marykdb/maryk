package maryk.core.query.responses

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.DataObjectWithMetaData

/** Response with [objects] to an objects (Get/Scan) request to [dataModel] */
data class ObjectsResponse<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    val objects: List<DataObjectWithMetaData<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: QueryDataModel<ObjectsResponse<*, *>>(
        properties = object : PropertyDefinitions<ObjectsResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, ObjectsResponse<*, *>::dataModel)
                add(1, "objects", ListDefinition(
                    valueDefinition = SubModelDefinition(
                        dataModel = { DataObjectWithMetaData }
                    )
                ), ObjectsResponse<*, *>::objects)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ObjectsResponse(
            dataModel = map[0] as RootDataModel<Any, *>,
            objects = map[1] as List<DataObjectWithMetaData<Any>>
        )
    }
}
