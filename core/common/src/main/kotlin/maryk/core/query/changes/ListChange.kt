package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Defines changes to lists by [listValueChanges] */
data class ListChange internal constructor(
    val listValueChanges: List<ListValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.ListChange

    constructor(vararg listValueChange: ListValueChanges<*>): this(listValueChange.toList())

    internal object Properties : PropertyDefinitions<ListChange>() {
        init {
            add(0, "referenceListValueChangesPairs",
                ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { ListValueChanges }
                    )
                ),
                ListChange::listValueChanges
            )
        }
    }

    internal companion object: ReferenceMappedDataModel<ListChange, ListValueChanges<*>, Properties, ListValueChanges.Properties>(
        properties = Properties,
        containedDataModel = ListValueChanges,
        referenceProperty = ListValueChanges.Properties.reference
    ) {
        override fun invoke(map: ValueMap<ListChange, Properties>) = ListChange(
            listValueChanges = map(0)
        )

        override fun writeJson(map: ValueMap<ListChange, Properties>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            writeReferenceValueMap(writer, map[0] as List<ListValueChanges<*>>, context)
        }

        override fun writeJson(obj: ListChange, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeReferenceValueMap(writer, obj.listValueChanges, context)
        }
    }
}
