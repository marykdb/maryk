package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Defines changes to lists by [listValueChanges] */
data class ListChange internal constructor(
    val listValueChanges: List<ListValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.ListChange

    constructor(vararg listValueChange: ListValueChanges<*>): this(listValueChange.toList())

    object Properties : ObjectPropertyDefinitions<ListChange>() {
        @Suppress("unused")
        val referenceListValueChangesPairs = add(1, "referenceListValueChangesPairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ListValueChanges }
                )
            ),
            ListChange::listValueChanges
        )
    }

    companion object: ReferenceMappedDataModel<ListChange, ListValueChanges<*>, Properties, ListValueChanges.Properties>(
        properties = Properties,
        containedDataModel = ListValueChanges,
        referenceProperty = ListValueChanges.Properties.reference
    ) {
        override fun invoke(map: ObjectValues<ListChange, Properties>) = ListChange(
            listValueChanges = map(1)
        )

        override fun writeJson(obj: ListChange, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeReferenceValueMap(writer, obj.listValueChanges, context)
        }
    }
}
