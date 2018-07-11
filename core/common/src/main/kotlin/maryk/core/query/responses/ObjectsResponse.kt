package maryk.core.query.responses

import maryk.core.models.RootObjectDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataObjectWithMetaData

/** Response with [objects] to an objects (Get/Scan) request to [dataModel] */
data class ObjectsResponse<DO: Any, out DM: RootObjectDataModel<DO, *>>(
    override val dataModel: DM,
    val objects: List<DataObjectWithMetaData<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: SimpleQueryDataModel<ObjectsResponse<*, *>>(
        properties = object : ObjectPropertyDefinitions<ObjectsResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, ObjectsResponse<*, *>::dataModel)
                add(1, "objects", ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { DataObjectWithMetaData }
                    )
                ), ObjectsResponse<*, *>::objects)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<ObjectsResponse<*, *>>) = ObjectsResponse(
            dataModel = map<RootObjectDataModel<Any, *>>(0),
            objects = map(1)
        )
    }
}
