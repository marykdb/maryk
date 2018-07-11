package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.DataObjectWithMetaData

/** Response with [objects] to an objects (Get/Scan) request to [dataModel] */
data class ObjectsResponse<DO: Any, out DM: IsRootDataModel<*>>(
    override val dataModel: DM,
    val objects: List<DataObjectWithMetaData<DM, DO>>
) : IsDataModelResponse<DM> {
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
        override fun invoke(map: SimpleObjectValues<ObjectsResponse<*, *>>) = ObjectsResponse(
            dataModel = map(0),
            objects = map<List<DataObjectWithMetaData<IsRootDataModel<*>, Any>>>(1)
        )
    }
}
