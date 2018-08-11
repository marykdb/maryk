package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Defines changes to sets by [setValueChanges] */
data class SetChange internal constructor(
    val setValueChanges: List<SetValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.SetChange

    constructor(vararg setValueChange: SetValueChanges<*>): this(setValueChange.toList())

    object Properties : ObjectPropertyDefinitions<SetChange>() {
        @Suppress("unused")
        val setValueChanges = add(1, "setValueChanges",
            ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { SetValueChanges }
                )
            ),
            SetChange::setValueChanges
        )
    }

    companion object: ReferenceMappedDataModel<SetChange, SetValueChanges<*>, Properties, SetValueChanges.Properties>(
        properties = SetChange.Properties,
        containedDataModel = SetValueChanges,
        referenceProperty = SetValueChanges.Properties.reference
    ) {
        override fun invoke(map: ObjectValues<SetChange, SetChange.Properties>) = SetChange(
            setValueChanges = map(1)
        )

        override fun writeJson(obj: SetChange, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeReferenceValueMap(writer, obj.setValueChanges, context)
        }
    }
}
