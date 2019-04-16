package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Defines changes to sets by [setValueChanges] */
data class SetChange internal constructor(
    val setValueChanges: List<SetValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.SetChange

    constructor(vararg setValueChange: SetValueChanges<*>) : this(setValueChange.toList())

    object Properties : ObjectPropertyDefinitions<SetChange>() {
        init {
            add(1, "setValueChanges",
                ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { SetValueChanges }
                    )
                ),
                SetChange::setValueChanges
            )
        }
    }

    companion object : ReferenceMappedDataModel<SetChange, SetValueChanges<*>, Properties, SetValueChanges.Properties>(
        properties = Properties,
        containedDataModel = SetValueChanges,
        referenceProperty = SetValueChanges.Properties.reference
    ) {
        override fun invoke(values: ObjectValues<SetChange, Properties>) = SetChange(
            setValueChanges = values(1)
        )

        override fun writeJson(obj: SetChange, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.setValueChanges, context)
        }
    }
}
