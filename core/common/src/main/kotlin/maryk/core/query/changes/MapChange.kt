package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Defines changes to maps by [mapValueChanges] */
data class MapChange internal constructor(
    val mapValueChanges: List<MapValueChanges<*, *>>
) : IsChange {
    override val changeType = ChangeType.MapChange

    constructor(vararg mapValueChange: MapValueChanges<*, *>): this(mapValueChange.toList())

    internal object Properties : PropertyDefinitions<MapChange>() {
        init {
            add(0, "mapValueChanges",
                ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { MapValueChanges }
                    )
                ),
                MapChange::mapValueChanges
            )
        }
    }

    internal companion object: ReferenceMappedDataModel<MapChange, MapValueChanges<*, *>, Properties, MapValueChanges.Properties>(
        properties = MapChange.Properties,
        containedDataModel = MapValueChanges,
        referenceProperty = MapValueChanges.Properties.reference
    ) {
        override fun invoke(map: ValueMap<MapChange, MapChange.Properties>) = MapChange(
            mapValueChanges = map(0)
        )

        override fun writeJson(map: ValueMap<MapChange, MapChange.Properties>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            writeReferenceValueMap(
                writer,
                map[0] as List<MapValueChanges<*, *>>,
                context
            )
        }

        override fun writeJson(obj: MapChange, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeReferenceValueMap(writer, obj.mapValueChanges, context)
        }
    }
}
