package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException

/** Defines changes to lists by [listValueChanges] */
data class ListChange internal constructor(
    val listValueChanges: List<ListValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.ListChange

    constructor(vararg listValueChange: ListValueChanges<*>): this(listValueChange.toList())

    internal object Properties : ObjectPropertyDefinitions<ListChange>() {
        val referenceListValueChangesPairs = add(0, "referenceListValueChangesPairs",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ListValueChanges }
                )
            ),
            ListChange::listValueChanges
        )
    }

    internal companion object: ReferenceMappedDataModel<ListChange, ListValueChanges<*>, Properties, ListValueChanges.Properties>(
        properties = Properties,
        containedDataModel = ListValueChanges,
        referenceProperty = ListValueChanges.Properties.reference
    ) {
        override fun invoke(map: ObjectValues<ListChange, Properties>) = ListChange(
            listValueChanges = map(0)
        )

        override fun writeJson(map: ObjectValues<ListChange, Properties>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeReferenceValueMap(
                writer,
                map { referenceListValueChangesPairs } ?: throw ParseException("Missing referenceListValueChangesPairs in ListChange"),
                context
            )
        }

        override fun writeJson(obj: ListChange, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeReferenceValueMap(writer, obj.listValueChanges, context)
        }
    }
}
