package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.RequestContext
import maryk.json.IsJsonLikeWriter

/** Defines changes to maps by [mapValueChanges] */
data class MapChange internal constructor(
    val mapValueChanges: List<MapValueChanges<*, *>>
) : IsChange {
    override val changeType = ChangeType.MapChange

    constructor(vararg mapValueChange: MapValueChanges<*, *>): this(mapValueChange.toList())

    object Properties : ObjectPropertyDefinitions<MapChange>() {
        @Suppress("unused")
        val mapValueChanges = add(1, "mapValueChanges",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { MapValueChanges }
                )
            ),
            MapChange::mapValueChanges
        )
    }

    companion object: ReferenceMappedDataModel<MapChange, MapValueChanges<*, *>, Properties, MapValueChanges.Properties>(
        properties = Properties,
        containedDataModel = MapValueChanges,
        referenceProperty = MapValueChanges.Properties.reference
    ) {
        override fun invoke(map: ObjectValues<MapChange, MapChange.Properties>) = MapChange(
            mapValueChanges = map(1)
        )

        override fun writeJson(obj: MapChange, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.mapValueChanges, context)
        }
    }
}
